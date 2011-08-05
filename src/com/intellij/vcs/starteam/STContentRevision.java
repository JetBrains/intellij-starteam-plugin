package com.intellij.vcs.starteam;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;

/**
* Created by IntelliJ IDEA.
* User: lloix
* Date: Feb 21, 2007
*/
class STContentRevision implements ContentRevision
{
  private final FilePath revisionPath;
  private final StarteamVcs host;
  private String  content;

  public STContentRevision(final StarteamVcs host, FilePath path)
  {
    this.host = host;
    revisionPath = path;
  }

  public String getContent() throws VcsException
  {
    if( content == null )
    {
      byte[] byteContent = host.getFileContent( StarteamChangeProvider.getSTCanonicPath( revisionPath.getPath() ) );
      content = new String( byteContent );
    }

    return content;
  }

  @NotNull public VcsRevisionNumber getRevisionNumber(){  return VcsRevisionNumber.NULL;  }
  @NotNull public FilePath getFile()                   {  return revisionPath; }
}
