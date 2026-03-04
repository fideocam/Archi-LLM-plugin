/**
 * ArchiGPT View - LLM integration for Archi ArchiMate modeling tool.
 * Provides a prompt textbox to interact with the open model via an LLM.
 */
package com.archimatetool.archigpt;

import java.io.IOException;
import java.util.List;

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

        Button sendButton = new Button(parent, SWT.PUSH);
        sendButton.setText("Ask ArchiGPT");
        sendButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        sendButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onSendPrompt();
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

    private void onSendPrompt() {
        String prompt = promptText.getText().trim();
        if (prompt.isEmpty()) {
            responseText.setText("Please enter a prompt.");
            return;
        }
        if (IEditorModelManager.INSTANCE.getOpenModels().isEmpty()) {
            responseText.setText("Open an ArchiMate model first. ArchiGPT will use it as context.");
            return;
        }

        final Text responseWidget = responseText;
        responseText.setText("Connecting to Ollama…");

        Job job = new Job("ArchiGPT – Ollama") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                String toShow;
                try {
                    OllamaClient client = new OllamaClient();
                    String raw = client.generateWithSystemPrompt(ArchiMateSystemPrompt.SYSTEM_PROMPT, prompt);
                    ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(raw);
                    List<String> errors = ArchiMateSchemaValidator.validate(parsed);
                    if (!errors.isEmpty()) {
                        toShow = "Validation failed (ArchiMate 3.2 schema):\n\n" + String.join("\n", errors)
                                + "\n\nRaw LLM response:\n" + raw;
                    } else if (parsed.getError() != null && !parsed.getError().isEmpty()) {
                        toShow = "LLM reported: " + parsed.getError() + "\n\nRaw:\n" + raw;
                    } else {
                        List<IArchimateModel> open = IEditorModelManager.INSTANCE.getOpenModels();
                        if (open.isEmpty()) {
                            toShow = "No open model to import into.\n\nParsed: " + parsed.getElements().size() + " elements, " + parsed.getRelationships().size() + " relationships.";
                        } else {
                            ArchiMateLLMImporter.importIntoModel(parsed, open.get(0));
                            toShow = "Imported into model \"" + open.get(0).getName() + "\": " + parsed.getElements().size() + " elements, " + parsed.getRelationships().size() + " relationships.";
                        }
                    }
                } catch (IOException e) {
                    toShow = "Error: " + e.getMessage() + "\n\nEnsure Ollama is running (e.g. ollama serve) and the model is available (e.g. ollama run " + OllamaClient.DEFAULT_MODEL + ").";
                }
                final String msg = toShow;
                Display.getDefault().asyncExec(() -> {
                    if (responseWidget != null && !responseWidget.isDisposed()) {
                        responseWidget.setText(msg);
                    }
                });
                return Status.OK_STATUS;
            }
        };
        job.schedule();
    }

    @Override
    public void setFocus() {
        if (promptText != null && !promptText.isDisposed()) {
            promptText.setFocus();
        }
    }
}
