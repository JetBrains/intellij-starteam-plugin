package com.intellij.vcs.starteam.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.starteam.StarteamBundle;
import com.intellij.vcs.starteam.StarteamVcs;
import com.intellij.vcsUtil.VcsUtil;

import java.util.ArrayList;

/**
 * @author mike
 */
public class CheckoutAction extends BasicAction
{
  protected void perform(Project project, final StarteamVcs activeVcs, final VirtualFile file) throws VcsException
  {
    //  Starteam does not support issuing the CheckOut command on the folder
    //  per se, thus base class has to enumerate over project structure for
    //  non-folder entries below the directory and issue the command for each
    //  of them. 
    if( !file.isDirectory() )
    {
      activeVcs.checkoutFile( file.getPresentableUrl() );
    }
    else
    {
      ArrayList<VirtualFile> fileList = new ArrayList<VirtualFile>();
      VcsUtil.collectFiles( file, fileList, true, false );
      for( VirtualFile vFile : fileList )
      {
        activeVcs.checkoutFile( vFile.getPresentableUrl() );
      }
    }
  }


  protected boolean isEnabled( Project project, AbstractVcs vcs, VirtualFile file ) {
    return FileStatusManager.getInstance( project ).getStatus( file ) != FileStatus.ADDED;
  }

  protected String getActionName() {
    return StarteamBundle.message("local.vcs.action.name.checkout.files");
  }
}
