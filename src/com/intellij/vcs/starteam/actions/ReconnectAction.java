package com.intellij.vcs.starteam.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.starteam.StarteamBundle;
import com.intellij.vcs.starteam.StarteamVcs;

/**
 * @author ddmoore
 */
public class ReconnectAction extends BasicAction {
  protected boolean isEnabled(Project project, AbstractVcs vcs, VirtualFile file) {
    return true;
  }

  protected String getActionName() {
    return StarteamBundle.message("local.vcs.action.name.reconnecting");
  }

  protected void perform(Project project, StarteamVcs activeVcs, VirtualFile file) throws VcsException {
    activeVcs.doShutdown();
    activeVcs.doStart();
  }
}
