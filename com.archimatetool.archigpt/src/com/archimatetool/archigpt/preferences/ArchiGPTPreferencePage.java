package com.archimatetool.archigpt.preferences;

import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.archimatetool.archigpt.ArchiGPTPreferences;
import com.archimatetool.archigpt.LlmContextConfig;
import com.archimatetool.archigpt.OllamaClient;

/**
 * Window → Preferences → ArchiGPT: Ollama server URL.
 */
@SuppressWarnings("nls")
public class ArchiGPTPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    public static final String ID = "com.archimatetool.archigpt.preferences";

    private Text baseUrlText;

    public static void openDialog(Shell shell) {
        PreferencesUtil.createPreferenceDialogOn(shell, ID, new String[] { ID }, null).open();
    }

    @Override
    public void init(IWorkbench workbench) {
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite body = new Composite(parent, SWT.NONE);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        body.setLayout(new GridLayout(2, false));

        Label urlLabel = new Label(body, SWT.NONE);
        urlLabel.setText("Ollama server URL:");
        baseUrlText = new Text(body, SWT.BORDER);
        baseUrlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        baseUrlText.setMessage(OllamaClient.DEFAULT_BASE_URL);

        Label hint = new Label(body, SWT.WRAP);
        hint.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
        hint.setText("Default is " + OllamaClient.DEFAULT_BASE_URL + " (Ollama on this machine). "
                + "To use Ollama on another computer on the LAN, enter its address, for example "
                + "http://192.168.1.10:11434 or just 192.168.1.10. "
                + "That host must listen on the network (e.g. OLLAMA_HOST=0.0.0.0).\n\n"
                + "Context size (num_ctx) is set in the ArchiGPT view next to the model list. "
                + "Check Use model max to request each model's full window from Ollama.\n\n"
                + "You can also set -D" + LlmContextConfig.PROP_OLLAMA_BASE_URL
                + " in Archi.ini (vmargs); that overrides this field.");

        Button test = new Button(body, SWT.PUSH);
        test.setText("Test connection");
        test.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
        test.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                runTestConnection();
            }
        });

        loadFromPreferences();
        applyJvmOverrideState();
        return body;
    }

    private void loadFromPreferences() {
        baseUrlText.setText(ArchiGPTPreferences.getBaseUrl());
    }

    private void applyJvmOverrideState() {
        if (LlmContextConfig.hasExplicitOllamaBaseUrl()) {
            baseUrlText.setEnabled(false);
            baseUrlText.setToolTipText("Server URL is fixed by -D" + LlmContextConfig.PROP_OLLAMA_BASE_URL + "="
                    + System.getProperty(LlmContextConfig.PROP_OLLAMA_BASE_URL).trim());
        }
    }

    private String urlFromField() {
        return LlmContextConfig.resolveOllamaBaseUrl(baseUrlText.getText());
    }

    private void runTestConnection() {
        String url = urlFromField();
        baseUrlText.setText(url);
        OllamaClient client = new OllamaClient(url, OllamaClient.DEFAULT_MODEL);
        if (client.checkConnection()) {
            setMessage("Connection OK (" + url + ").", IMessageProvider.INFORMATION);
            setErrorMessage(null);
        } else {
            setMessage("Could not reach Ollama at " + url
                    + ". Check the address, that Ollama is running, and that it listens on the network.",
                    IMessageProvider.WARNING);
        }
    }

    private void savePreferences() throws org.osgi.service.prefs.BackingStoreException {
        if (LlmContextConfig.hasExplicitOllamaBaseUrl()) {
            return;
        }
        ArchiGPTPreferences.setBaseUrl(urlFromField());
    }

    @Override
    public boolean performOk() {
        try {
            savePreferences();
            setErrorMessage(null);
            return true;
        } catch (Exception ex) {
            setErrorMessage(ex.getMessage() != null ? ex.getMessage() : ex.toString());
            return false;
        }
    }

    @Override
    protected void performDefaults() {
        baseUrlText.setText(OllamaClient.DEFAULT_BASE_URL);
        applyJvmOverrideState();
    }
}
