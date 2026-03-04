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
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.part.ViewPart;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;

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

        Composite buttonBar = new Composite(parent, SWT.NONE);
        buttonBar.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        GridLayout buttonLayout = new GridLayout(2, false);
        buttonLayout.marginWidth = 0;
        buttonLayout.marginHeight = 0;
        buttonLayout.horizontalSpacing = 8;
        buttonBar.setLayout(buttonLayout);

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

    private static String buildUserMessage(String selectionContext, String prompt) {
        if (selectionContext == null || selectionContext.isEmpty()) {
            return prompt;
        }
        return selectionContext + "\n\nUser request: " + prompt;
    }

    private void onStopRequest() {
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
        final String userMessage = buildUserMessage(selectionContext, prompt);

        final Text responseWidget = responseText;
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
                String toShow;
                try {
                    OllamaClient client = new OllamaClient();
                    if (!client.checkConnection()) {
                        finishRequest("Cannot reach Ollama at " + OllamaClient.DEFAULT_BASE_URL + ". Is Ollama running? (e.g. ollama serve)");
                        return Status.OK_STATUS;
                    }
                    updateStatus("Connection OK. Sending request to Ollama. Waiting for response (this may take a minute)…");
                    String raw;
                    try {
                        raw = client.generateWithSystemPrompt(ArchiMateSystemPrompt.SYSTEM_PROMPT, userMessage, currentConnectionRef);
                    } catch (IOException e) {
                        if (monitor.isCanceled()) {
                            finishRequest("Cancelled. Ollama request stopped.");
                            return Status.CANCEL_STATUS;
                        }
                        throw e;
                    }
                    if (monitor.isCanceled()) {
                        finishRequest("Cancelled.");
                        return Status.CANCEL_STATUS;
                    }
                    updateStatus("Response received. Parsing and validating…");
                    ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(raw);
                    List<String> errors = ArchiMateSchemaValidator.validate(parsed);
                    if (!errors.isEmpty()) {
                        toShow = "Validation failed (ArchiMate 3.2 schema):\n\n" + String.join("\n", errors)
                                + "\n\nRaw LLM response:\n" + raw;
                    } else if (parsed.getError() != null && !parsed.getError().isEmpty()) {
                        toShow = "LLM reported: " + parsed.getError() + "\n\nRaw:\n" + raw;
                    } else {
                        List<IArchimateModel> open = IEditorModelManager.INSTANCE.getModels();
                        if (open.isEmpty()) {
                            toShow = "No open model to import into.\n\nParsed: " + parsed.getElements().size() + " elements, " + parsed.getRelationships().size() + " relationships.";
                        } else {
                            ArchiMateLLMImporter.importIntoModel(parsed, open.get(0));
                            toShow = "Imported into model \"" + open.get(0).getName() + "\": " + parsed.getElements().size() + " elements, " + parsed.getRelationships().size() + " relationships.";
                        }
                    }
                } catch (IOException e) {
                    if (monitor.isCanceled()) {
                        finishRequest("Cancelled. Ollama request stopped.");
                        return Status.CANCEL_STATUS;
                    }
                    toShow = "Error: " + e.getMessage() + "\n\nEnsure Ollama is running (e.g. ollama serve) and the model is available (e.g. ollama run " + OllamaClient.DEFAULT_MODEL + ").";
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
