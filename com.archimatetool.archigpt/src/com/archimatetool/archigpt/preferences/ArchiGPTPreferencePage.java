package com.archimatetool.archigpt.preferences;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.archimatetool.archigpt.ArchiGPTPreferences;
import com.archimatetool.archigpt.LLMClient;
import com.archimatetool.archigpt.LlmClientFactory;

/**
 * Window → Preferences → ArchiGPT: LLM provider, API key, base URL, and model.
 */
@SuppressWarnings("nls")
public class ArchiGPTPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    public static final String ID = "com.archimatetool.archigpt.preferences";

    private static final String[][] PROVIDER_CHOICES = {
            { "Ollama (local)", ArchiGPTPreferences.PROVIDER_OLLAMA },
            { "OpenAI", ArchiGPTPreferences.PROVIDER_OPENAI },
            { "Azure OpenAI", ArchiGPTPreferences.PROVIDER_AZURE_OPENAI },
            { "Anthropic", ArchiGPTPreferences.PROVIDER_ANTHROPIC },
            { "Google Gemini", ArchiGPTPreferences.PROVIDER_GOOGLE },
            { "Custom (OpenAI-compatible)", ArchiGPTPreferences.PROVIDER_CUSTOM },
    };

    private Combo providerCombo;
    private Text apiKeyText;
    private Text baseUrlText;
    private Text modelText;
    private Text maxOutTokensText;
    private Text estContextTokensText;

    @Override
    public void init(IWorkbench workbench) {
        noDefaultAndApplyButton();
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite body = new Composite(parent, SWT.NONE);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        body.setLayout(new GridLayout(2, false));

        Label provLabel = new Label(body, SWT.NONE);
        provLabel.setText("Provider:");
        providerCombo = new Combo(body, SWT.READ_ONLY);
        for (String[] row : PROVIDER_CHOICES) {
            providerCombo.add(row[0]);
        }
        providerCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        selectProviderInCombo(ArchiGPTPreferences.getProvider());

        Label keyLabel = new Label(body, SWT.NONE);
        keyLabel.setText("API key (external providers):");
        apiKeyText = new Text(body, SWT.BORDER | SWT.PASSWORD);
        apiKeyText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        apiKeyText.setMessage("Not used for Ollama");

        Label urlLabel = new Label(body, SWT.NONE);
        urlLabel.setText("Base URL:");
        baseUrlText = new Text(body, SWT.BORDER);
        baseUrlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label modelLabel = new Label(body, SWT.NONE);
        modelLabel.setText("Model / deployment:");
        modelText = new Text(body, SWT.BORDER);
        modelText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Group adv = new Group(body, SWT.NONE);
        adv.setText("Context (non-Ollama)");
        adv.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
        adv.setLayout(new GridLayout(2, false));

        Label maxOutL = new Label(adv, SWT.NONE);
        maxOutL.setText("Max output tokens:");
        maxOutTokensText = new Text(adv, SWT.BORDER);
        maxOutTokensText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label estL = new Label(adv, SWT.NONE);
        estL.setText("Estimated input context (tokens, for XML budget):");
        estContextTokensText = new Text(adv, SWT.BORDER);
        estContextTokensText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label hint = new Label(body, SWT.WRAP);
        hint.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
        hint.setText(
                "Privacy: when you use an external provider, your prompts and ArchiMate XML are sent to that service. "
                        + "Use Ollama for fully local inference.\n\n"
                        + "Azure OpenAI: set Base URL to the full chat completions address, including api-version query parameter. "
                        + "Gemini: Base URL is usually https://generativelanguage.googleapis.com/v1beta and the API key is a Google AI Studio key.");

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

        providerCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateHints();
            }
        });
        updateHints();

        return body;
    }

    private void selectProviderInCombo(String providerId) {
        for (int i = 0; i < PROVIDER_CHOICES.length; i++) {
            if (PROVIDER_CHOICES[i][1].equals(providerId)) {
                providerCombo.select(i);
                return;
            }
        }
        providerCombo.select(0);
    }

    private String getSelectedProviderId() {
        int i = providerCombo.getSelectionIndex();
        if (i < 0 || i >= PROVIDER_CHOICES.length) {
            return ArchiGPTPreferences.PROVIDER_OLLAMA;
        }
        return PROVIDER_CHOICES[i][1];
    }

    private void loadFromPreferences() {
        selectProviderInCombo(ArchiGPTPreferences.getProvider());
        apiKeyText.setText(ArchiGPTPreferences.getApiKey());
        baseUrlText.setText(ArchiGPTPreferences.getBaseUrl());
        modelText.setText(ArchiGPTPreferences.getModel());
        maxOutTokensText.setText(String.valueOf(ArchiGPTPreferences.getMaxOutputTokens()));
        estContextTokensText.setText(String.valueOf(ArchiGPTPreferences.getEstimatedContextTokens()));
    }

    private void updateHints() {
        boolean ollama = ArchiGPTPreferences.PROVIDER_OLLAMA.equals(getSelectedProviderId());
        apiKeyText.setEnabled(!ollama);
    }

    private void runTestConnection() {
        try {
            validateHttps();
        } catch (IllegalArgumentException ex) {
            setMessage(ex.getMessage(), IMessageProvider.ERROR);
            return;
        }
        if (!performOkWritesToPreferences()) {
            return;
        }
        try {
            LlmClientFactory.validateConfiguration();
            LLMClient c = LlmClientFactory.createClient();
            if (c.checkConnection()) {
                setMessage("Connection OK (" + c.endpointSummary() + ").", IMessageProvider.INFORMATION);
            } else {
                setMessage("Could not reach or authenticate with " + c.endpointSummary() + ".", IMessageProvider.WARNING);
            }
        } catch (IllegalArgumentException ex) {
            setMessage(ex.getMessage(), IMessageProvider.ERROR);
        } catch (Exception ex) {
            setMessage(ex.getMessage() != null ? ex.getMessage() : ex.toString(), IMessageProvider.ERROR);
        }
    }

    private boolean performOkWritesToPreferences() {
        try {
            savePreferences();
            return true;
        } catch (Exception ex) {
            setErrorMessage(ex.getMessage());
            return false;
        }
    }

    private void savePreferences() throws org.osgi.service.prefs.BackingStoreException {
        IEclipsePreferences n = ArchiGPTPreferences.node();
        n.put(ArchiGPTPreferences.P_PROVIDER, getSelectedProviderId());
        n.put(ArchiGPTPreferences.P_API_KEY, apiKeyText.getText());
        n.put(ArchiGPTPreferences.P_BASE_URL, baseUrlText.getText().trim());
        n.put(ArchiGPTPreferences.P_MODEL, modelText.getText().trim());
        n.put(ArchiGPTPreferences.P_MAX_OUTPUT_TOKENS, maxOutTokensText.getText().trim());
        n.put(ArchiGPTPreferences.P_ESTIMATED_CONTEXT_TOKENS, estContextTokensText.getText().trim());
        ArchiGPTPreferences.flush();
    }

    @Override
    public boolean performOk() {
        try {
            validateHttps();
            savePreferences();
            setErrorMessage(null);
            return true;
        } catch (IllegalArgumentException ex) {
            setErrorMessage(ex.getMessage());
            return false;
        } catch (Exception ex) {
            setErrorMessage(ex.getMessage() != null ? ex.getMessage() : ex.toString());
            return false;
        }
    }

    private void validateHttps() {
        String p = getSelectedProviderId();
        if (ArchiGPTPreferences.PROVIDER_OLLAMA.equals(p)) {
            return;
        }
        String u = baseUrlText.getText().trim().toLowerCase();
        if (u.startsWith("http://") && !u.startsWith("http://localhost") && !u.startsWith("http://127.0.0.1")) {
            throw new IllegalArgumentException("External providers must use https:// (http://localhost is allowed for development).");
        }
    }

    @Override
    protected void performDefaults() {
        selectProviderInCombo(ArchiGPTPreferences.PROVIDER_OLLAMA);
        apiKeyText.setText("");
        baseUrlText.setText(com.archimatetool.archigpt.OllamaClient.DEFAULT_BASE_URL);
        modelText.setText(com.archimatetool.archigpt.OllamaClient.DEFAULT_MODEL);
        maxOutTokensText.setText("16384");
        estContextTokensText.setText("200000");
        updateHints();
    }
}
