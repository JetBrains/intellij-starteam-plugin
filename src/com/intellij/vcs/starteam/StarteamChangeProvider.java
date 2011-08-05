package com.intellij.vcs.starteam;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.starbase.starteam.Folder;
import com.starbase.starteam.ServerException;
import com.starbase.starteam.Status;
import com.starbase.starteam.TypeNotFoundException;
import com.starbase.starteam.vts.comm.CommandException;

import java.io.File;
import java.util.HashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 */

public class StarteamChangeProvider implements ChangeProvider
{
  private final Project     project;
  private final StarteamVcs host;
  private boolean     warnShown;

  private final HashSet<String> filesNew = new HashSet<String>();
  private final HashSet<String> filesChanged = new HashSet<String>();
  private final HashSet<String> filesIgnored = new HashSet<String>();

  public StarteamChangeProvider( Project project, StarteamVcs host )
  {
    this.project = project;
    this.host = host;
    warnShown = false;
  }

  public boolean isModifiedDocumentTrackingRequired() {  return false;  }

  public void doCleanup(final List<VirtualFile> files) {
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress,
                         final ChangeListManagerGate addGate)
  {
    initInternals();
    try
    {
      iterateOverScope( dirtyScope, builder );
      iterateOverDirtyFiles( dirtyScope, builder );

      addNewAndRenamedFiles( builder );
      addChangedFiles( builder );
      addRemovedFiles( builder );
      addIgnoredFiles( builder );
    }
    //  User description: When we are not connected to the network. We get the following error.
    //  Error message: Connection reset by peer: socket write error
    catch( final CommandException e ){
      processFailedConnection( StarteamBundle.message("message.text.network.connection.fail") );
    }
    //  Error message: The server does not recognize the client.
    //  The client may have been automatically logged off due to inactivity.
    catch( ServerException e ) {
      processFailedConnection( StarteamBundle.message("message.text.lost.connection") );
    }
    catch( TypeNotFoundException e ){
      processFailedConnection( StarteamBundle.message("message.text.expired.license") );
    }
  }

  private void processFailedConnection( final String msg )
  {
    if( !warnShown )
    {
      Runnable action = new Runnable()
      {
        public void run()
        {
          String title = StarteamBundle.message("message.title.configuration.error");
          String reconnectText = StarteamBundle.message("text.reconnect");
          String cancelText = StarteamBundle.message("text.cancel");
          int result = Messages.showChooseDialog( project, msg, title, Messages.getQuestionIcon(), 
                                                  new String[] { reconnectText, cancelText }, reconnectText );
          if( result == 0 )
          {
            try
            {
              host.doShutdown();
              host.doStart();
              warnShown = false;
            }
            catch( VcsException e )
            {
              Messages.showErrorDialog( msg, StarteamBundle.message("message.title.configuration.error") );
            }
          }
        }
      };
      ApplicationManager.getApplication().invokeLater( action );
      warnShown = true;
    }
  }

  private void iterateOverScope( VcsDirtyScope scope, ChangelistBuilder builder )
  {
    for( FilePath path : scope.getRecursivelyDirtyDirectories() )
      iterateOverDirectories( path.getPath(), builder );
  }

  private void iterateOverDirectories( String path, final ChangelistBuilder builder )
  {
    VirtualFile folder = VcsUtil.getVirtualFile( path );
    if( folder != null && !host.isFileIgnored( folder ))
    {
      VirtualFile[] childs = folder.getChildren();
      for( VirtualFile vf : childs )
      {
        FilePath filepath = VcsUtil.getFilePath( vf.getPath() );
        processItem( filepath, builder );
      }
    }
  }

  private void iterateOverDirtyFiles( final VcsDirtyScope dirtyScope,
                                      final ChangelistBuilder builder )
  {
    for( FilePath path : dirtyScope.getDirtyFiles() )
    {
      processItem( path, builder );
    }
  }

  private void processItem(FilePath path, final ChangelistBuilder builder)
  {
    //  Filter out al files which are located within the project root on the HD
    //  but are not in the structure of the project.
    if( VcsUtil.isFileForVcs( path, project, StarteamVcsAdapter.getInstance(project) ) )
    {
      if( path.isDirectory() )
      {
        processFolder( path, builder );
        iterateOverDirectories( path.getPath(), builder );
      }
      else
        processFile( path, builder );
    }
  }

  //---------------------------------------------------------------------------
  //  Get information on files that differ from the Starteam project location:
  //  - different content
  //  - not present in Starteam repository (added locally)
  //  - absent in local directory (this information is not used currently).
  //---------------------------------------------------------------------------
  private void processFile(final FilePath filePath, final ChangelistBuilder builder)
  {
    String  path = filePath.getPath();

    //  Files that match with the ignored patterns are processed separately.
    if( host.isFileIgnored( filePath.getVirtualFile() ) )
    {
      filesIgnored.add( path );
      return;
    }

    com.starbase.starteam.File file = host.findFile( getSTCanonicPath( filePath ) );

    try
    {
      if( file == null )
      {
        if( !isFileUnderRenamedDir( path ) && isProperNotification( filePath ) )
        {
          filesNew.add( path );
        }
      }
      else
      {
        //  In certain cases we still get status "UNKNOWN" (int 6) after the
        //  particular amount of time (even after full resync). Try to refresh.
        try { file.updateStatus(false, true); }
        catch( Exception e )
        {
          //  Nothing to do - if <updateStatus> throws an exception then most
          //  probably we deal with latest version
        }

        int status = file.getStatus();
        if( status == Status.NEW )
          filesNew.add( path );
        else
        if( status == Status.MERGE )
          builder.processChange( new Change( new STContentRevision(host, filePath ), new CurrentContentRevision( filePath ), FileStatus.MERGE ),
                                 StarteamVcs.getKey());
        else
        if( status == Status.MODIFIED )
          filesChanged.add( path );
        else
        if( status == Status.MISSING )
        {
          //  We have two source of information on locally deleted files:
          //  - one is stored in StarteamVcs host as a list controllable by VFS listener
          //  - here, on folder traverse.
          //  So do not duplicate files in the dirty lists.

          String normPath = filePath.getPath().replace( File.separatorChar, '/');
          if( !host.removedFiles.contains( normPath ))
            builder.processLocallyDeletedFile( filePath );
        }
      }
    }
    catch( Exception e )
    {
      //  By default if any exception happens, we consider file status to be
      // "unknown" and do not indicate any change.
    }
  }

  private void processFolder( final FilePath filePath, final ChangelistBuilder builder )
  {
    String  path = filePath.getPath();

    if( !isProperNotification( filePath ) )
      return;
    
    //  Files that match with the ignored patterns are processed separately.
    if( host.isFileIgnored( filePath.getVirtualFile() ) )
    {
      filesIgnored.add( filePath.getPath() );
      return;
    }

    Folder stFolder = host.findFolder( getSTCanonicPath( filePath ) );
    try
    {
      //  Process two cases:
      //  - directory is added locally
      //  - directory is renamed locally
      if( stFolder == null )
      {
          String oldPath = host.renamedDirs.get( filePath.getPath() );
          if( oldPath != null )
          {
            //  For the renamed file we receive two change requests: one for
            //  the old dir and one for the new one. Ignore the first request.

            FilePath oldName = VcsUtil.getFilePath( oldPath );

            //  Check whether we perform "undo" of the rename. This is easily
            //  done if we want to undo the refactoring of the package rename.
            builder.processChange( new Change( new STContentRevision( host, oldName ), new STContentRevision( host, filePath )), StarteamVcs.getKey());
            /*
            else
            {
              host.renamedDirs.remove( filePath.getPath() );
            }
            */
//            host.setWorkingFolderName( oldName.getIOFile().getPath(), filePath.getVirtualFile().getName() );
            host.setWorkingFolderName( oldName.getIOFile().getPath(), filePath.getName() );
          }
          else
          {
            filesNew.add( path );
          }
      }
      //  Check for the folder can come from either singular calls (when
      //  dirtyRecursive is empty) or from batch call when we need to observe
      //  the whole project.
      //  This case (Folder != null) comes from the batch mode.
      else
      {
        String oldPath = host.renamedDirs.get( filePath.getPath() );
        if( oldPath != null )
        {
          FilePath oldName = VcsUtil.getFilePath( oldPath );
          builder.processChange( new Change( new STContentRevision( host, oldName ), new STContentRevision( host, filePath )), StarteamVcs.getKey());
        }
      }
    }
    catch( Exception e )
    {
      //  By default if any exception happens, we consider file status to be
      // "unknown" and do not indicate any change.
    }
  }

  private void addNewAndRenamedFiles( final ChangelistBuilder builder )
  {
    for( String path : filesNew )
    {
      FilePath newFP = VcsUtil.getFilePath( path );
      String   oldName = host.renamedFiles.get( path );
      if( host.containsNew( path ) )
      {
        builder.processChange( new Change( null, new CurrentContentRevision( newFP ) ), StarteamVcs.getKey());
      }
      else
      if( oldName == null )
      {
        VirtualFile vFile = VcsUtil.getVirtualFile( path );
        builder.processUnversionedFile( vFile );
      }
      else
      {
        ContentRevision before = new STContentRevision( host, VcsUtil.getFilePath( oldName ) );
        builder.processChange( new Change( before, new CurrentContentRevision( newFP )), StarteamVcs.getKey());
      }
    }
  }

  private void addChangedFiles( final ChangelistBuilder builder )
  {
    for( String path : filesChanged )
    {
      final FilePath fp = VcsUtil.getFilePath( path );
      builder.processChange( new Change( new STContentRevision( host, fp ), new CurrentContentRevision( fp )), StarteamVcs.getKey());
    }
  }

  private void addRemovedFiles( final ChangelistBuilder builder )
  {
    final HashSet<String> files = new HashSet<String>();
    files.addAll( host.removedFolders );
    files.addAll( host.removedFiles );

    for( String path : files )
      builder.processLocallyDeletedFile( VcsUtil.getFilePath( path ) );
  }

  private void addIgnoredFiles( final ChangelistBuilder builder )
  {
    for( String path : filesIgnored )
      builder.processIgnoredFile( VcsUtil.getVirtualFile( path ) );
  }

  /**
   * For the renamed or moved file we receive two change requests: one for
   * the old file and one for the new one. For renamed file old request differs
   * in filename, for the moved one - in parent path name. This request must be
   * ignored since all preliminary information is already accumulated.
   */
  private static boolean isProperNotification( final FilePath filePath )
  {
    String oldName = filePath.getName();
    String newName = (filePath.getVirtualFile() == null) ? "" : filePath.getVirtualFile().getName();
    String oldParent = filePath.getVirtualFileParent().getPath();
    String newParent = filePath.getPath().substring( 0, filePath.getPath().length() - oldName.length() - 1 );
    return (newParent.equals( oldParent ) && newName.equals( oldName ) );
  }

  private boolean isFileUnderRenamedDir( String path )
  {
    for( String newPath : host.renamedDirs.keySet() )
    {
      if( path.startsWith( newPath ) && !path.equalsIgnoreCase( newPath ) )
        return true;
    }
    return false;
  }

  public static String getSTCanonicPath( String path )
  {
    return path.replace('/', File.separatorChar);
  }

  public static String getSTCanonicPath( FilePath file )
  {
    return file.getPath().replace('/', File.separatorChar);
  }

  private void initInternals()
  {
    filesNew.clear();
    filesChanged.clear();
    filesIgnored.clear();
  }
}
