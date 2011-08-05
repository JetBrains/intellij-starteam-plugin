package com.intellij.vcs.starteam.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.starteam.StarteamBundle;
import com.intellij.vcs.starteam.StarteamVcs;

/**
 * @author mike
 */
public class RefreshAction extends BasicAction
{
  protected String getActionName() {
    return StarteamBundle.message("local.vcs.action.name.refresh");
  }

  protected boolean isEnabled(Project project, AbstractVcs vcs, VirtualFile file) {
    return true;
  }

  protected void perform( final Project project, StarteamVcs activeVcs, VirtualFile file) throws VcsException
  {
    try{
      activeVcs.refresh();
      ApplicationManager.getApplication().runReadAction( new Runnable() {
        public void run() {  VcsDirtyScopeManager.getInstance( project ).markEverythingDirty(); }
      });
    }
    catch(VcsException ex){
      Messages.showMessageDialog(project, ex.getMessage(), StarteamBundle.message("message.title.action.error"), Messages.getErrorIcon());
    }
  }
}
