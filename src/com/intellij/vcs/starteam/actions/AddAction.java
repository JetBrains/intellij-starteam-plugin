package com.intellij.vcs.starteam.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.starteam.StarteamBundle;
import com.intellij.vcs.starteam.StarteamVcs;

/**
 * @author LloiX
 */
public class AddAction extends BasicAction
{
  protected void perform(Project project, StarteamVcs vcs, VirtualFile file) throws VcsException
  {
    //  Perform only moving the file into normal changelist with the
    //  proper status "ADDED". After that the file can be submitted into
    //  the repository via "Commit" dialog.
    vcs.add2NewFile( file );

    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance( project );
    mgr.fileDirty( file );
    file.refresh( true, true );
  }

  protected boolean isEnabled( Project project, AbstractVcs vcs, VirtualFile file )
  {
    FileStatus status = FileStatusManager.getInstance( project ).getStatus( file );
    return !file.isDirectory() && (status == FileStatus.UNKNOWN);
  }

  protected String getActionName() {
    return StarteamBundle.message("action.name.adding.files");
  }
}
