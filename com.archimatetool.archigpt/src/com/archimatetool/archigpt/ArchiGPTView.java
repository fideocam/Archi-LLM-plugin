/**
 * ArchiGPT View - LLM integration for Archi ArchiMate modeling tool.
 * Provides a prompt textbox to interact with the open model via an LLM.
 */
package com.archimatetool.archigpt;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import java.net.HttpURLConnection;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.part.ViewPart;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IFolder;

import org.eclipse.ui.IEditorPart;

/**
 * ArchiGPT view with a text area for the user prompt and a button to send to the LLM.
 */
@SuppressWarnings("nls")
public class ArchiGPTView extends ViewPart {

    public static final String ID = "com.archimatetool.archigpt.view";

    private Text promptText;
    private Text responseText;
    private Button sendButton;
    private Button stopButton;
    private Job currentJob;
    /** Holder for the active HTTP connection so Stop can disconnect it and abort the Ollama request. */
    private volatile AtomicReference<HttpURLConnection> currentConnectionRef;
    /** Set when user clicks Stop so the job can treat IOException as cancel even before monitor is updated. */
    private volatile boolean userRequestedCancel;

    @Override
    public void createPartControl(Composite parent) {
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 10;
        layout.marginHeight = 10;
        layout.verticalSpacing = 8;
        parent.setLayout(layout);

        Label promptLabel = new Label(parent, SWT.NONE);
        promptLabel.setText("Prompt:");
        promptLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        promptText = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData promptData = new GridData(SWT.FILL, SWT.FILL, true, true);
        promptData.minimumHeight = 120;
        promptData.heightHint = 120;
        promptText.setLayoutData(promptData);
        promptText.setMessage("Describe the change you want to make to your ArchiMate model...");
        promptText.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
                    if ((e.stateMask & SWT.SHIFT) == 0) {
                        e.doit = false;
                        onSendPrompt();
                    }
                }
            }
        });

        Composite buttonBar = new Composite(parent, SWT.NONE);
        buttonBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout buttonLayout = new GridLayout(3, false);
        buttonLayout.marginWidth = 0;
        buttonLayout.marginHeight = 0;
        buttonLayout.horizontalSpacing = 8;
        buttonBar.setLayout(buttonLayout);
        Label filler = new Label(buttonBar, SWT.NONE);
        filler.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        sendButton = new Button(buttonBar, SWT.PUSH);
        sendButton.setText("Ask ArchiGPT");
        sendButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        sendButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onSendPrompt();
            }
        });

        stopButton = new Button(buttonBar, SWT.PUSH);
        stopButton.setText("Stop");
        stopButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        stopButton.setVisible(false);
        stopButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onStopRequest();
            }
        });

        Label responseLabel = new Label(parent, SWT.NONE);
        responseLabel.setText("Response:");
        responseLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        responseText = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        GridData responseData = new GridData(SWT.FILL, SWT.FILL, true, true);
        responseData.minimumHeight = 150;
        responseData.heightHint = 150;
        responseText.setLayoutData(responseData);
    }

    private void setRequestInProgress(boolean inProgress) {
        if (promptText == null || promptText.isDisposed()) return;
        Runnable update = () -> {
            if (promptText.isDisposed()) return;
            promptText.setEditable(!inProgress);
            promptText.setForeground(promptText.getDisplay().getSystemColor(
                    inProgress ? SWT.COLOR_DARK_GRAY : SWT.COLOR_WIDGET_FOREGROUND));
            sendButton.setEnabled(!inProgress);
            stopButton.setVisible(inProgress);
        };
        if (Display.getCurrent() != null) {
            update.run();
        } else {
            Display.getDefault().asyncExec(update);
        }
    }

    private static String buildUserMessage(String selectionContext, String modelXml, String prompt) {
        StringBuilder sb = new StringBuilder();
        if (modelXml != null && !modelXml.isEmpty()) {
            sb.append("Supplied ArchiMate model (XML):\n\n").append(modelXml).append("\n\n");
        }
        if (selectionContext != null && !selectionContext.isEmpty()) {
            sb.append(selectionContext).append("\n\n");
        }
        sb.append("User request: ").append(prompt);
        return sb.toString();
    }

    private void onStopRequest() {
        userRequestedCancel = true;
        responseText.setText("Cancelling…");
        if (currentJob != null) {
            currentJob.cancel();
        }
        HttpURLConnection conn = currentConnectionRef != null ? currentConnectionRef.get() : null;
        if (conn != null) {
            try {
                conn.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "\n\n... (truncated)";
    }

    private void onSendPrompt() {
        String prompt = promptText.getText().trim();
        if (prompt.isEmpty()) {
            responseText.setText("Please enter a prompt.");
            return;
        }
        if (IEditorModelManager.INSTANCE.getModels().isEmpty()) {
            responseText.setText("Open an ArchiMate model first. ArchiGPT will use it as context.");
            return;
        }

        String selectionContext = "";
        try {
            IWorkbenchWindow window = getViewSite().getWorkbenchWindow();
            if (window != null && window.getSelectionService() != null) {
                selectionContext = SelectionContextBuilder.buildFromSelectionService(window.getSelectionService());
            }
        } catch (Exception e) {
            // ignore; proceed without selection context
        }
        String modelXml = "";
        List<IArchimateModel> openModels = IEditorModelManager.INSTANCE.getModels();
        if (!openModels.isEmpty()) {
            modelXml = ModelContextToXml.toXml(openModels.get(0));
        }
        final String userMessage = buildUserMessage(selectionContext, modelXml, prompt);
        final String suppliedModelXml = modelXml;

        final Text responseWidget = responseText;
        userRequestedCancel = false;
        currentConnectionRef = new AtomicReference<>();
        setRequestInProgress(true);
        responseText.setText("Connecting to Ollama…");

        currentJob = new Job("ArchiGPT – Ollama") {
            private void updateStatus(String message) {
                Display.getDefault().asyncExec(() -> {
                    if (responseWidget != null && !responseWidget.isDisposed()) {
                        responseWidget.setText(message);
                    }
                });
            }

            private void finishRequest(String message) {
                Display.getDefault().asyncExec(() -> {
                    if (responseWidget != null && !responseWidget.isDisposed()) {
                        responseWidget.setText(message);
                    }
                    setRequestInProgress(false);
                    currentJob = null;
                });
            }

            @Override
            protected IStatus run(IProgressMonitor monitor) {
                String raw = null;
                String toShow = "";
                try {
                    OllamaClient client = new OllamaClient();
                    if (!client.checkConnection()) {
                        finishRequest("Cannot reach Ollama at " + OllamaClient.DEFAULT_BASE_URL + ". Is Ollama running? (e.g. ollama serve)");
                        return Status.OK_STATUS;
                    }
                    updateStatus("Connection OK. Sending request to Ollama. Waiting for response (this may take a minute)…");
                    try {
                        raw = client.generateWithSystemPrompt(ArchiMateSystemPrompt.SYSTEM_PROMPT, userMessage, currentConnectionRef);
                    } catch (IOException e) {
                        if (userRequestedCancel || monitor.isCanceled()) {
                            finishRequest("Cancelled. Ollama request stopped.");
                            return Status.CANCEL_STATUS;
                        }
                        throw e;
                    }
                    if (userRequestedCancel || monitor.isCanceled()) {
                        finishRequest("Cancelled.");
                        return Status.CANCEL_STATUS;
                    }
                    updateStatus("Response received. Parsing and validating…");
                    ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(raw);
                    boolean hasImportData = !parsed.getElements().isEmpty() || !parsed.getRelationships().isEmpty();
                    if (parsed.getError() != null && !parsed.getError().isEmpty()) {
                        toShow = "LLM reported: " + parsed.getError() + "\n\nRaw LLM response:\n" + truncate(raw, 4000);
                    } else if (!hasImportData) {
                        toShow = "Analysis result:\n\n" + raw;
                    } else {
                        List<String> errors = ArchiMateSchemaValidator.validate(parsed);
                        if (!errors.isEmpty()) {
                            toShow = "Validation failed (ArchiMate 3.2 schema):\n\n" + String.join("\n", errors)
                                    + "\n\nRaw LLM response:\n" + truncate(raw, 4000);
                        } else {
                            final ArchiMateLLMResult resultToImport = parsed;
                            final String[] importMessage = new String[1];
                            Display.getDefault().syncExec(() -> {
                                List<IArchimateModel> open = IEditorModelManager.INSTANCE.getModels();
                                if (open.isEmpty()) {
                                    importMessage[0] = "No open model to import into.\n\nParsed: " + resultToImport.getElements().size() + " elements, " + resultToImport.getRelationships().size() + " relationships.";
                                } else {
                                    IArchimateModel model = open.get(0);
                                    IFolder targetFolder = null;
                                    IArchimateDiagramModel targetDiagram = null;
                                    try {
                                        IWorkbenchWindow window = getViewSite().getWorkbenchWindow();
                                        if (window != null) {
                                            if (window.getSelectionService() != null) {
                                                Object sel = window.getSelectionService().getSelection();
                                                if (sel instanceof IStructuredSelection) {
                                                    Object first = ((IStructuredSelection) sel).getFirstElement();
                                                    if (first instanceof IFolder) {
                                                        targetFolder = (IFolder) first;
                                                    } else if (first instanceof IArchimateConcept) {
                                                        Object container = ((IArchimateConcept) first).eContainer();
                                                        if (container instanceof IFolder) {
                                                            targetFolder = (IFolder) container;
                                                        }
                                                    } else if (first instanceof IArchimateDiagramModel && model.equals(((IArchimateDiagramModel) first).getArchimateModel())) {
                                                        targetDiagram = (IArchimateDiagramModel) first;
                                                    }
                                                }
                                            }
                                            if (targetDiagram == null && window.getActivePage() != null) {
                                                IEditorPart editor = window.getActivePage().getActiveEditor();
                                                if (editor != null) {
                                                    Object adapter = editor.getAdapter(IDiagramModel.class);
                                                    if (adapter instanceof IArchimateDiagramModel) {
                                                        IArchimateDiagramModel diagram = (IArchimateDiagramModel) adapter;
                                                        if (model.equals(diagram.getArchimateModel())) {
                                                            targetDiagram = diagram;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception ignored) {
                                    }
                                    ArchiMateLLMImporter.importIntoModel(resultToImport, model, targetFolder, targetDiagram);
                                    StringBuilder where = new StringBuilder();
                                    if (targetFolder != null) {
                                        where.append(" into selected folder \"").append(targetFolder.getName()).append("\"");
                                    }
                                    if (targetDiagram != null) {
                                        if (where.length() > 0) where.append(" and");
                                        where.append(" added to view \"").append(targetDiagram.getName()).append("\"");
                                    }
                                    if (where.length() > 0) where.insert(0, " ");
                                    importMessage[0] = "Imported into model \"" + model.getName() + "\"" + where + ": " + resultToImport.getElements().size() + " elements, " + resultToImport.getRelationships().size() + " relationships.";
                                }
                            });
                            toShow = importMessage[0] + "\n\nRaw LLM response:\n" + truncate(raw, 4000);
                        }
                    }
                } catch (IOException e) {
                    if (userRequestedCancel || monitor.isCanceled()) {
                        finishRequest("Cancelled. Ollama request stopped.");
                        return Status.CANCEL_STATUS;
                    }
                    toShow = "Error: " + e.getMessage() + "\n\nEnsure Ollama is running (e.g. ollama serve) and the model is available (e.g. ollama run " + OllamaClient.DEFAULT_MODEL + ").";
                    if (raw != null) {
                        toShow += "\n\nRaw LLM response:\n" + truncate(raw, 4000);
                    }
                } catch (Throwable t) {
                    toShow = "Error while parsing or importing: " + t.getMessage()
                            + (raw != null ? "\n\nRaw LLM response:\n" + truncate(raw, 4000) : "");
                }
                if (suppliedModelXml != null && !suppliedModelXml.isEmpty() && toShow != null && !toShow.isEmpty()) {
                    toShow = "Raw ArchiMate model (XML) sent to LLM:\n\n" + suppliedModelXml + "\n\n---\n\n" + toShow;
                }
                finishRequest(toShow);
                return Status.OK_STATUS;
            }
        };
        currentJob.schedule();
    }

    @Override
    public void setFocus() {
        if (promptText != null && !promptText.isDisposed()) {
            promptText.setFocus();
        }
    }
}
