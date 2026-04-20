package com.archimatetool.archigpt.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.archimatetool.archigpt.preferences.ArchiGPTPreferencePage;

/**
 * Opens Window → Preferences on the ArchiGPT page.
 */
@SuppressWarnings("nls")
public class OpenArchiGPTPreferencesHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow() != null
                ? PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell()
                : null;
        PreferencesUtil.createPreferenceDialogOn(shell, ArchiGPTPreferencePage.ID, new String[] { ArchiGPTPreferencePage.ID },
                null).open();
        return null;
    }
}
