package com.intellij.vcs.starteam;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ReadOnlyAttributeUtil;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Apr 3, 2007
 */
public class StarteamEditFileProvider implements EditFileProvider
{
  private final StarteamVcs host;

  public StarteamEditFileProvider( StarteamVcs host )
  {
    this.host = host;
  }

  public String getRequestText() {  return StarteamBundle.message("message.text.checkout.question");  }

  public void editFiles( VirtualFile[] files )
  {
    for( final VirtualFile file : files )
    {
      try
      {
        host.checkoutFile( file.getPath(), false );
      }
      catch( VcsException e )
      {
        Messages.showErrorDialog( e.getLocalizedMessage(), StarteamBundle.message("message.title.operation.failed.error"));

        ApplicationManager.getApplication().runWriteAction( new Runnable() { public void run(){
          try {   ReadOnlyAttributeUtil.setReadOnlyAttribute( file, false );  }
          catch( IOException e ) {
            Messages.showErrorDialog( StarteamBundle.message("message.text.ro.set.error", file.getPath()),
                                      StarteamBundle.message("message.title.operation.failed.error"));
          }
        } });
      }
      file.refresh( true, false );
    }
  }
}
