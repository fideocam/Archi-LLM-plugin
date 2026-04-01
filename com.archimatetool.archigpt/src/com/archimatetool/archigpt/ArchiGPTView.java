/**
 * ArchiGPT View - LLM integration for Archi ArchiMate modeling tool.
 * Provides a prompt textbox to interact with the open model via an LLM.
 */
package com.archimatetool.archigpt;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.part.ViewPart;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IFolder;

import org.eclipse.ui.IEditorPart;
import org.osgi.framework.FrameworkUtil;

/**
 * ArchiGPT view with a text area for the user prompt and a button to send to the LLM.
 */
@SuppressWarnings("nls")
public class ArchiGPTView extends ViewPart {

    public static final String ID = "com.archimatetool.archigpt.view";

    /** Max characters of model XML to send so the request fits typical Ollama context (4k–8k tokens). Kept conservative so the LLM receives the full message. */
    private static final int LLM_MAX_XML_CHARS = 12_000;

    private Text promptText;
    private Text whatWasSentSummaryText;
    private Text xmlPreviewText;
    private Text responseText;
    private Button sendButton;
    private Button saveAsButton;
    private Label buildLabel;
    private Label whatWasSentLabel;
    private Label xmlPreviewLabel;
    private Job currentJob;
    /** Cached selection from model tree/diagram so it is still used when ArchiGPT view has focus. */
    private volatile IStructuredSelection lastModelSelection;
    private ISelectionListener selectionListener;
    /** Holder for the active HTTP connection so Stop can disconnect it and abort the Ollama request. */
    private volatile AtomicReference<HttpURLConnection> currentConnectionRef;
    /** Set when user clicks Stop so the job can treat IOException as cancel even before monitor is updated. */
    private volatile boolean userRequestedCancel;

    @Override
    public void createPartControl(Composite parent) {
        GridLayout rootLayout = new GridLayout(1, false);
        rootLayout.marginWidth = 0;
        rootLayout.marginHeight = 0;
        parent.setLayout(rootLayout);

        CTabFolder tabFolder = new CTabFolder(parent, SWT.TOP | SWT.BORDER);
        tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // ---- Main tab: prompt + button + response ----
        CTabItem mainTab = new CTabItem(tabFolder, SWT.NONE);
        mainTab.setText("ArchiGPT");
        Composite mainComposite = new Composite(tabFolder, SWT.NONE);
        mainTab.setControl(mainComposite);
        GridLayout mainLayout = new GridLayout(1, false);
        mainLayout.marginWidth = 10;
        mainLayout.marginHeight = 10;
        mainLayout.verticalSpacing = 8;
        mainComposite.setLayout(mainLayout);

        Label promptLabel = new Label(mainComposite, SWT.NONE);
        promptLabel.setText("Prompt:");
        promptLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        promptText = new Text(mainComposite, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
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
                        if (currentJob != null) onStopRequest(); else onSendPrompt();
                    }
                }
            }
        });

        Composite buttonBar = new Composite(mainComposite, SWT.NONE);
        buttonBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout buttonLayout = new GridLayout(2, false);
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
                if (currentJob != null) onStopRequest(); else onSendPrompt();
            }
        });

        Label responseLabel = new Label(mainComposite, SWT.NONE);
        responseLabel.setText("Response:");
        responseLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        responseText = new Text(mainComposite, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        GridData responseData = new GridData(SWT.FILL, SWT.FILL, true, true);
        responseData.minimumHeight = 200;
        responseData.heightHint = 200;
        responseText.setLayoutData(responseData);

        saveAsButton = new Button(mainComposite, SWT.PUSH);
        saveAsButton.setText("Save as…");
        saveAsButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        saveAsButton.setEnabled(false);
        saveAsButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onSaveAsResponse();
            }
        });

        // ---- Debug tab: version, what was sent, model XML ----
        CTabItem debugTab = new CTabItem(tabFolder, SWT.NONE);
        debugTab.setText("Debug");
        Composite debugComposite = new Composite(tabFolder, SWT.NONE);
        debugTab.setControl(debugComposite);
        GridLayout debugLayout = new GridLayout(1, false);
        debugLayout.marginWidth = 10;
        debugLayout.marginHeight = 10;
        debugLayout.verticalSpacing = 8;
        debugComposite.setLayout(debugLayout);

        buildLabel = new Label(debugComposite, SWT.NONE);
        buildLabel.setText("ArchiGPT " + getBuildVersion());
        buildLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        whatWasSentLabel = new Label(debugComposite, SWT.NONE);
        whatWasSentLabel.setText("What was sent to the LLM (last request):");
        whatWasSentLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        whatWasSentSummaryText = new Text(debugComposite, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        GridData whatWasSentData = new GridData(SWT.FILL, SWT.FILL, true, false);
        whatWasSentData.minimumHeight = 70;
        whatWasSentData.heightHint = 70;
        whatWasSentSummaryText.setLayoutData(whatWasSentData);
        whatWasSentSummaryText.setMessage("(Prompt, selection, and XML length appear here when you click Ask ArchiGPT)");

        xmlPreviewLabel = new Label(debugComposite, SWT.NONE);
        xmlPreviewLabel.setText("Model XML sent to LLM (exact payload):");
        xmlPreviewLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        xmlPreviewText = new Text(debugComposite, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        GridData xmlPreviewData = new GridData(SWT.FILL, SWT.FILL, true, true);
        xmlPreviewData.minimumHeight = 200;
        xmlPreviewData.heightHint = 200;
        xmlPreviewText.setLayoutData(xmlPreviewData);
        xmlPreviewText.setMessage("(XML sent to the LLM appears here when you click Ask ArchiGPT)");

        tabFolder.setSelection(0);

        selectionListener = (part, selection) -> {
            if (selection != null && SelectionContextBuilder.isModelSelection(selection)) {
                lastModelSelection = (IStructuredSelection) selection;
            }
        };
        getViewSite().getPage().addSelectionListener(selectionListener);
    }

    @Override
    public void dispose() {
        if (selectionListener != null && getViewSite() != null && getViewSite().getPage() != null) {
            getViewSite().getPage().removeSelectionListener(selectionListener);
            selectionListener = null;
        }
        super.dispose();
    }

    private static String getBuildVersion() {
        try {
            return "v" + FrameworkUtil.getBundle(ArchiGPTView.class).getVersion().toString();
        } catch (Exception e) {
            return "build unknown";
        }
    }

    private void setRequestInProgress(boolean inProgress) {
        if (promptText == null || promptText.isDisposed()) return;
        Runnable update = () -> {
            if (promptText.isDisposed()) return;
            promptText.setEditable(!inProgress);
            promptText.setForeground(promptText.getDisplay().getSystemColor(
                    inProgress ? SWT.COLOR_DARK_GRAY : SWT.COLOR_WIDGET_FOREGROUND));
            sendButton.setText(inProgress ? "Stop ArchiGPT" : "Ask ArchiGPT");
        };
        if (Display.getCurrent() != null) {
            update.run();
        } else {
            Display.getDefault().asyncExec(update);
        }
    }

    /** Collect folders to put first in the XML (selected folder or the folder containing the selected element); only for {@code model}. */
    private static List<IFolder> getPriorityFoldersFromSelection(IStructuredSelection selection, IArchimateModel model) {
        if (selection == null || selection.isEmpty() || model == null) return new ArrayList<>();
        Set<IFolder> seen = new LinkedHashSet<>();
        List<IFolder> list = new ArrayList<>();
        for (Iterator<?> it = selection.iterator(); it.hasNext(); ) {
            Object obj = it.next();
            if (obj instanceof IFolder) {
                IFolder f = (IFolder) obj;
                if (model.equals(f.getArchimateModel()) && !seen.contains(f)) {
                    seen.add(f);
                    list.add(f);
                }
            } else if (obj instanceof IArchimateConcept) {
                Object container = ((IArchimateConcept) obj).eContainer();
                if (container instanceof IFolder) {
                    IFolder f = (IFolder) container;
                    if (model.equals(f.getArchimateModel()) && !seen.contains(f)) {
                        seen.add(f);
                        list.add(f);
                    }
                }
            }
        }
        return list;
    }

    /** Collect diagrams to put first in the XML (selected view, diagram canvas selection, or views that contain the selected element). */
    private static List<IArchimateDiagramModel> getPriorityDiagramsFromSelection(IStructuredSelection selection, IArchimateModel model) {
        if (selection == null || selection.isEmpty() || model == null) return new ArrayList<>();
        Set<IArchimateDiagramModel> seen = new LinkedHashSet<>();
        List<IArchimateDiagramModel> list = new ArrayList<>();
        for (Iterator<?> it = selection.iterator(); it.hasNext(); ) {
            Object obj = it.next();
            if (obj instanceof IArchimateDiagramModel) {
                IArchimateDiagramModel dm = (IArchimateDiagramModel) obj;
                if (model.equals(dm.getArchimateModel()) && !seen.contains(dm)) {
                    seen.add(dm);
                    list.add(dm);
                }
            } else if (obj instanceof IDiagramModelArchimateObject) {
                IDiagramModel dm = ((IDiagramModelArchimateObject) obj).getDiagramModel();
                if (dm instanceof IArchimateDiagramModel) {
                    IArchimateDiagramModel adm = (IArchimateDiagramModel) dm;
                    if (model.equals(adm.getArchimateModel()) && !seen.contains(adm)) {
                        seen.add(adm);
                        list.add(adm);
                    }
                }
            } else if (obj instanceof IDiagramModelConnection) {
                IDiagramModel dm = ((IDiagramModelConnection) obj).getDiagramModel();
                if (dm instanceof IArchimateDiagramModel) {
                    IArchimateDiagramModel adm = (IArchimateDiagramModel) dm;
                    if (model.equals(adm.getArchimateModel()) && !seen.contains(adm)) {
                        seen.add(adm);
                        list.add(adm);
                    }
                }
            } else if (obj instanceof IArchimateConcept) {
                for (IArchimateDiagramModel dm : ModelContextToXml.getDiagramsContaining(model, (IArchimateConcept) obj)) {
                    if (!seen.contains(dm)) { seen.add(dm); list.add(dm); }
                }
            }
        }
        return list;
    }

    private static IFolder resolveTargetFolder(IStructuredSelection selection, IArchimateModel model) {
        if (selection == null || selection.isEmpty() || model == null) return null;
        Object first = selection.getFirstElement();
        if (first instanceof IFolder) {
            IFolder f = (IFolder) first;
            return model.equals(f.getArchimateModel()) ? f : null;
        }
        if (first instanceof IArchimateConcept) {
            Object container = ((IArchimateConcept) first).eContainer();
            if (container instanceof IFolder) {
                IFolder f = (IFolder) container;
                return model.equals(f.getArchimateModel()) ? f : null;
            }
        }
        return null;
    }

    private static IArchimateDiagramModel resolveTargetDiagram(IStructuredSelection selection, IArchimateModel model, IWorkbenchWindow window) {
        if (model == null) return null;
        if (selection != null && !selection.isEmpty()) {
            Object first = selection.getFirstElement();
            if (first instanceof IArchimateDiagramModel && model.equals(((IArchimateDiagramModel) first).getArchimateModel())) {
                return (IArchimateDiagramModel) first;
            }
            if (first instanceof IDiagramModelArchimateObject) {
                IDiagramModel dm = ((IDiagramModelArchimateObject) first).getDiagramModel();
                if (dm instanceof IArchimateDiagramModel) {
                    IArchimateDiagramModel adm = (IArchimateDiagramModel) dm;
                    if (model.equals(adm.getArchimateModel())) return adm;
                }
            }
            if (first instanceof IDiagramModelConnection) {
                IDiagramModel dm = ((IDiagramModelConnection) first).getDiagramModel();
                if (dm instanceof IArchimateDiagramModel) {
                    IArchimateDiagramModel adm = (IArchimateDiagramModel) dm;
                    if (model.equals(adm.getArchimateModel())) return adm;
                }
            }
        }
        try {
            if (window != null && window.getActivePage() != null) {
                IEditorPart editor = window.getActivePage().getActiveEditor();
                if (editor != null) {
                    Object adapter = editor.getAdapter(IDiagramModel.class);
                    if (adapter instanceof IArchimateDiagramModel) {
                        IArchimateDiagramModel diagram = (IArchimateDiagramModel) adapter;
                        if (model.equals(diagram.getArchimateModel())) return diagram;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Prefer the model for the active Archi editor; otherwise infer from selection (tree or diagram canvas);
     * then cached model-tree selection; finally the first open model. Avoids sending the wrong model when several are open.
     */
    private static IArchimateModel resolveActiveModel(IWorkbenchWindow window, IStructuredSelection workbenchSelection,
            IStructuredSelection cachedModelSelection, List<IArchimateModel> openModels) {
        if (openModels == null || openModels.isEmpty()) return null;
        try {
            if (window != null && window.getActivePage() != null) {
                IEditorPart editor = window.getActivePage().getActiveEditor();
                if (editor != null) {
                    IArchimateModel fromEditor = editor.getAdapter(IArchimateModel.class);
                    if (fromEditor != null && isInOpenList(fromEditor, openModels)) {
                        return fromEditor;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        IArchimateModel fromSelection = getModelFromStructuredSelection(workbenchSelection);
        if (fromSelection != null && isInOpenList(fromSelection, openModels)) {
            return fromSelection;
        }
        fromSelection = getModelFromStructuredSelection(cachedModelSelection);
        if (fromSelection != null && isInOpenList(fromSelection, openModels)) {
            return fromSelection;
        }
        return openModels.get(0);
    }

    private static boolean isInOpenList(IArchimateModel model, List<IArchimateModel> openModels) {
        if (model == null) return false;
        for (IArchimateModel m : openModels) {
            if (m == model) return true;
        }
        return false;
    }

    private static IArchimateModel getModelFromStructuredSelection(IStructuredSelection selection) {
        if (selection == null || selection.isEmpty()) return null;
        for (Iterator<?> it = selection.iterator(); it.hasNext(); ) {
            Object obj = it.next();
            if (obj instanceof IArchimateModelObject) {
                IArchimateModel m = ((IArchimateModelObject) obj).getArchimateModel();
                if (m != null) return m;
            }
        }
        return null;
    }

    /**
     * Views whose names appear in the prompt (case-insensitive), longest names first to reduce false matches.
     */
    private static List<IArchimateDiagramModel> diagramsMentionedInPrompt(IArchimateModel model, String prompt) {
        if (model == null || prompt == null) return new ArrayList<>();
        String pl = prompt.trim().toLowerCase(Locale.ROOT);
        if (pl.isEmpty()) return new ArrayList<>();
        List<IArchimateDiagramModel> all = new ArrayList<>(ModelContextToXml.getAllDiagramModels(model));
        all.sort((a, b) -> Integer.compare(diagramNameLen(b), diagramNameLen(a)));
        Set<IArchimateDiagramModel> seen = new LinkedHashSet<>();
        List<IArchimateDiagramModel> out = new ArrayList<>();
        for (IArchimateDiagramModel dm : all) {
            String n = dm.getName();
            if (n == null) continue;
            String nl = n.trim().toLowerCase(Locale.ROOT);
            if (nl.length() < 4) continue;
            if (pl.contains(nl) && seen.add(dm)) {
                out.add(dm);
            }
        }
        return out;
    }

    private static int diagramNameLen(IArchimateDiagramModel d) {
        return d != null && d.getName() != null ? d.getName().length() : 0;
    }

    private static List<IArchimateDiagramModel> mergeDiagramPriorities(IArchimateModel model, String prompt,
            List<IArchimateDiagramModel> fromSelection) {
        List<IArchimateDiagramModel> fromPrompt = diagramsMentionedInPrompt(model, prompt);
        if (fromPrompt.isEmpty()) {
            return fromSelection != null ? fromSelection : new ArrayList<>();
        }
        Set<IArchimateDiagramModel> seen = new LinkedHashSet<>();
        List<IArchimateDiagramModel> merged = new ArrayList<>();
        for (IArchimateDiagramModel d : fromPrompt) {
            if (d != null && model.equals(d.getArchimateModel()) && seen.add(d)) {
                merged.add(d);
            }
        }
        if (fromSelection != null) {
            for (IArchimateDiagramModel d : fromSelection) {
                if (d != null && seen.add(d)) {
                    merged.add(d);
                }
            }
        }
        return merged;
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

    /** True if the response text contains a full ArchiMate Open Exchange model XML document. */
    private static boolean isFullModelXml(String text) {
        if (text == null) return false;
        return text.contains("<?xml") && text.contains("<model") && (text.contains("archimateModel") || text.contains("archimate"));
    }

    /** Extract the first complete Open Exchange XML document from the response, or return the full text. */
    private static String extractFullModelXml(String response) {
        if (response == null) return "";
        int xmlStart = response.indexOf("<?xml");
        if (xmlStart < 0) return response;
        int modelEnd = response.indexOf("</model>", xmlStart);
        if (modelEnd < 0) return response;
        return response.substring(xmlStart, modelEnd + "</model>".length()).trim();
    }

    private void onSaveAsResponse() {
        if (responseText == null || responseText.isDisposed()) return;
        String content = responseText.getText();
        if (content == null || content.isEmpty()) return;
        String toSave = isFullModelXml(content) ? extractFullModelXml(content) : content;
        org.eclipse.swt.widgets.Shell shell = getViewSite().getShell();
        if (shell == null) return;
        FileDialog dialog = new FileDialog(shell, SWT.SAVE);
        dialog.setFilterNames(new String[] { "ArchiMate model (.archimate)", "XML files (*.xml)", "All files (*.*)" });
        dialog.setFilterExtensions(new String[] { "*.archimate", "*.xml", "*.*" });
        dialog.setFileName("model.archimate");
        String path = dialog.open();
        if (path == null || path.isEmpty()) return;
        try {
            Files.write(new File(path).toPath(), toSave.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            responseText.getDisplay().asyncExec(() -> {
                org.eclipse.swt.widgets.MessageBox box = new org.eclipse.swt.widgets.MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
                box.setMessage("Could not save file: " + e.getMessage());
                box.setText("Save failed");
                box.open();
            });
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

        IStructuredSelection selectionToUse = null;
        try {
            IWorkbenchWindow window = getViewSite().getWorkbenchWindow();
            if (window != null && window.getSelectionService() != null) {
                ISelection current = window.getSelectionService().getSelection();
                if (SelectionContextBuilder.isModelSelection(current)) {
                    selectionToUse = (IStructuredSelection) current;
                }
                if (selectionToUse == null) {
                    selectionToUse = lastModelSelection;
                }
            }
        } catch (Exception e) {
            selectionToUse = lastModelSelection;
        }
        String selectionContext = selectionToUse != null ? SelectionContextBuilder.buildFromStructuredSelection(selectionToUse) : "";

        IWorkbenchWindow windowEarly = getViewSite() != null ? getViewSite().getWorkbenchWindow() : null;
        List<IArchimateModel> openModels = IEditorModelManager.INSTANCE.getModels();
        IArchimateModel model = resolveActiveModel(windowEarly, selectionToUse, lastModelSelection, openModels);

        // Build XML for LLM with relevant parts first; cap size so it fits Ollama context
        List<IFolder> priorityFolders = getPriorityFoldersFromSelection(selectionToUse, model);
        List<IArchimateDiagramModel> priorityDiagrams = mergeDiagramPriorities(model, prompt,
                getPriorityDiagramsFromSelection(selectionToUse, model));
        String modelXmlForRequest = model != null
                ? ModelContextToXml.toXml(model, LLM_MAX_XML_CHARS, priorityFolders, priorityDiagrams)
                : "";
        int fullXmlLength = model != null ? ModelContextToXml.toXml(model).length() : 0;

        // Show exactly what we send in the GUI so the user can verify the model is included
        int xmlLen = modelXmlForRequest != null ? modelXmlForRequest.length() : 0;
        StringBuilder summary = new StringBuilder();
        summary.append("Payload order: model XML first (views before folders within the cap), then your request.\n\n");
        if (model != null) {
            summary.append("Active model sent to Ollama: \"").append(model.getName() != null ? model.getName() : "").append("\"\n\n");
        }
        summary.append("Prompt: ").append(prompt).append("\n\n");
        summary.append("Selection context: ").append(selectionContext != null && !selectionContext.isEmpty() ? selectionContext.trim() : "(none)").append("\n\n");
        summary.append("Model XML: ").append(xmlLen).append(" characters sent (see box below). ");
        if (xmlLen > 0 && modelXmlForRequest != null) {
            String preview = modelXmlForRequest.length() > 120 ? modelXmlForRequest.substring(0, 120) + "…" : modelXmlForRequest;
            summary.append("Starts with: ").append(preview.replace("\n", " "));
        } else {
            summary.append("(No model open or model empty — open an ArchiMate model so its XML is sent to the LLM.)");
        }
        if (fullXmlLength > xmlLen) {
            summary.append("\n[Full model is ").append(fullXmlLength).append(" chars; truncated so the LLM receives the message.]");
        }
        if (whatWasSentSummaryText != null && !whatWasSentSummaryText.isDisposed()) {
            whatWasSentSummaryText.setText(summary.toString());
        }
        if (xmlPreviewText != null && !xmlPreviewText.isDisposed()) {
            xmlPreviewText.setText(modelXmlForRequest != null ? modelXmlForRequest : "");
        }
        IWorkbenchWindow window = windowEarly != null ? windowEarly : (getViewSite() != null ? getViewSite().getWorkbenchWindow() : null);
        final IFolder targetFolder = resolveTargetFolder(selectionToUse, model);
        final IArchimateDiagramModel targetDiagram = resolveTargetDiagram(selectionToUse, model, window);
        final IArchimateModel modelForImport = model;

        final String userMessage = UserMessageBuilder.buildUserMessage(selectionContext, modelXmlForRequest, prompt);

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
                    if (saveAsButton != null && !saveAsButton.isDisposed()) {
                        saveAsButton.setEnabled(isFullModelXml(message));
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
                        raw = client.generateWithSystemPrompt(ArchiMateSystemPrompt.SYSTEM_PROMPT, userMessage, currentConnectionRef, 32768);
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
                    boolean hasDiagram = parsed.getDiagram() != null && parsed.getDiagram().getName() != null && !parsed.getDiagram().getName().isEmpty();
                    boolean hasImportData = !parsed.getElements().isEmpty() || !parsed.getRelationships().isEmpty()
                            || !parsed.getRemoveElementIds().isEmpty() || !parsed.getRemoveRelationshipIds().isEmpty()
                            || !parsed.getRemoveDiagramNames().isEmpty()
                            || !parsed.getRemoveElementFromDiagramIds().isEmpty() || !parsed.getRemoveRelationshipFromDiagramIds().isEmpty()
                            || hasDiagram;
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
                                    int addEl = resultToImport.getElements().size(), addRel = resultToImport.getRelationships().size();
                                    int remEl = resultToImport.getRemoveElementIds().size(), remRel = resultToImport.getRemoveRelationshipIds().size();
                                    int remDiag = resultToImport.getRemoveDiagramNames().size();
                                    int remFromDiagEl = resultToImport.getRemoveElementFromDiagramIds().size();
                                    int remFromDiagRel = resultToImport.getRemoveRelationshipFromDiagramIds().size();
                                    importMessage[0] = "No open model to import into.\n\nParsed: " + addEl + " elements, " + addRel + " relationships to add; " + remEl + " elements, " + remRel + " relationships to remove from model; " + remFromDiagEl + " elements, " + remFromDiagRel + " relationships to remove from diagram only; " + remDiag + " diagrams to remove.";
                                } else if (modelForImport == null || !isInOpenList(modelForImport, open)) {
                                    importMessage[0] = "The ArchiMate model used for this request is no longer open; import skipped. Open the model and run the request again.";
                                } else {
                                    IArchimateModel model = modelForImport;
                                    ArchiMateLLMImporter.importIntoModel(resultToImport, model, targetFolder, targetDiagram);
                                    StringBuilder where = new StringBuilder();
                                    if (targetFolder != null) {
                                        where.append(" into selected folder \"").append(targetFolder.getName()).append("\"");
                                    }
                                    if (targetDiagram != null) {
                                        if (where.length() > 0) where.append(" and");
                                        where.append(" added to view \"").append(targetDiagram.getName()).append("\"");
                                    }
                                    if (resultToImport.getDiagram() != null && resultToImport.getDiagram().getName() != null) {
                                        if (where.length() > 0) where.append(".");
                                        where.append(" Created new view \"").append(resultToImport.getDiagram().getName()).append("\"");
                                    }
                                    if (where.length() > 0) where.insert(0, " ");
                                    int addEl = resultToImport.getElements().size(), addRel = resultToImport.getRelationships().size();
                                    int remEl = resultToImport.getRemoveElementIds().size(), remRel = resultToImport.getRemoveRelationshipIds().size();
                                    int remDiag = resultToImport.getRemoveDiagramNames().size();
                                    int remFromDiagEl = resultToImport.getRemoveElementFromDiagramIds().size();
                                    int remFromDiagRel = resultToImport.getRemoveRelationshipFromDiagramIds().size();
                                    String action = "Imported into model \"" + model.getName() + "\"" + where;
                                    if (addEl > 0 || addRel > 0) action += ": " + addEl + " elements, " + addRel + " relationships added.";
                                    if (remEl > 0 || remRel > 0) action += (addEl > 0 || addRel > 0 ? " " : ": ") + "Removed " + remEl + " elements, " + remRel + " relationships from model.";
                                    if (remFromDiagEl > 0 || remFromDiagRel > 0) action += (addEl > 0 || addRel > 0 || remEl > 0 || remRel > 0 ? " " : ": ") + "Removed " + remFromDiagEl + " element(s), " + remFromDiagRel + " relationship(s) from diagram only.";
                                    if (remDiag > 0) action += (addEl > 0 || addRel > 0 || remEl > 0 || remRel > 0 || remFromDiagEl > 0 || remFromDiagRel > 0 ? " " : ": ") + "Removed " + remDiag + " diagram(s).";
                                    boolean diagramCreated = resultToImport.getDiagram() != null && resultToImport.getDiagram().getName() != null && !resultToImport.getDiagram().getName().isEmpty();
                                    importMessage[0] = (addEl > 0 || addRel > 0 || remEl > 0 || remRel > 0 || remDiag > 0 || remFromDiagEl > 0 || remFromDiagRel > 0 || diagramCreated) ? action : "No changes applied.";
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
                String responseOnly = toShow != null ? toShow : "";
                finishRequest(responseOnly);
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
