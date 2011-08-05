package com.intellij.vcs.starteam;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.*;
import com.intellij.vcsUtil.VcsUtil;
import com.starbase.starteam.vts.comm.CommandException;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Jul 31, 2006
 */
public class VFSListener extends VirtualFileAdapter
{
  private final StarteamVcsAdapter adapter;
  private final StarteamVcs host;
  private final Project project;

  public VFSListener( StarteamVcs host, Project project )
  {
    this.project = project;
    this.host = host;
    adapter = StarteamVcsAdapter.getInstance( project );
  }

  public void propertyChanged(VirtualFilePropertyEvent event)
  {
    //  Trace renamed files only if we really working with Starteam.
    if(event.getPropertyName().equals(VirtualFile.PROP_NAME))
    {
      VirtualFile file = event.getFile();
      //  After any rename/move takes place, mark the affected items as dirty
      //  so that their status is refreshed after the operation.
      if( file.isDirectory() )
        VcsDirtyScopeManager.getInstance( host.getProject() ).dirDirtyRecursively( file, true );
      else
        VcsDirtyScopeManager.getInstance( host.getProject() ).fileDirty( file );
    }
  }

  public void beforeFileMovement(VirtualFileMoveEvent event)
  {
    if( !event.getFile().isDirectory() )
    {
      String oldName = event.getFile().getPath();
      String newName = event.getNewParent().getPath() + "/" + event.getFile().getName();

      String prevName = host.renamedFiles.get( oldName );
      if( host.existsFile( oldName ) || prevName != null )
      {
        //  Newer name must refer to the oldest one in the chain of movements
        if( prevName == null )
          prevName = oldName;

        //  Check whether we are trying to rename the file back -
        //  if so, just delete the old key-value pair
        if( !prevName.equals( newName ) )
          host.renamedFiles.put( newName, prevName );

        host.renamedFiles.remove( oldName );
      }
    }
  }

  public void beforePropertyChange(VirtualFilePropertyEvent event)
  {
    VirtualFile file = event.getFile();

    //  Trace deleted files only if we really working with Starteam.
    if( event.getPropertyName() == VirtualFile.PROP_NAME )
    {
      String parentDir = file.getParent().getPath() + "/";
      String currentName = parentDir + event.getOldValue();
      String newName = parentDir + event.getNewValue();

      String prevName = file.isDirectory() ? host.renamedDirs.get( currentName ) :
                                             host.renamedFiles.get( currentName );
      boolean existsItem = existsItem( file.isDirectory(), currentName );
      if( existsItem || prevName != null )
      {
        if( file.isDirectory() )
          processRename( host.renamedDirs, prevName, currentName, newName );
        else
          processRename( host.renamedFiles, prevName, currentName, newName );
      }
    }
  }

  /**
   * We can catch CommandException when the connection is broken. In order
   * not to miss the transaction, assume "true" in the worst case. 
   */
  private boolean existsItem( boolean isFolder, final String currentName )
  {
    boolean existsItem = true;
    try{
      existsItem =  isFolder && host.existsFolder( currentName ) ||
                   !isFolder && host.existsFile( currentName );
    }
    catch( CommandException e ){
      //  Nothing to do, assume "true" by default.
    }
    return existsItem;
  }

  private static void processRename( HashMap<String, String> renamedItems, String prevName, String currName, String newName )
  {
    //  Newer name must refer to the oldest one in the chain of renamings
    if( prevName == null )
      prevName = currName;

    //  Check whether we are trying to rename the file back -
    //  if so, just delete the old key-value pair
    if( !prevName.equals( newName ) )
      renamedItems.put( newName, prevName );

    renamedItems.remove( currName );
  }

  public void fileCreated( VirtualFileEvent event )
  {
    @NonNls final String TITLE = "Add file(s)";
    @NonNls final String MESSAGE = "Do you want to schedule the following file for addition to Starteam?\n{0}";

    VirtualFile file = event.getFile();
    String path = file.getPath();

    //  In the case of multi-vcs project configurations, we need to skip all
    //  notifications on non-owned files
    if( !VcsUtil.isFileForVcs( file, project, adapter ) )
      return;

    //  In the case when the project content is synchronized over the
    //  occasionally removed files.
    host.removedFiles.remove( path );
    host.removedFolders.remove( path );

    //  Do not ask user if the files created came from the vcs per se
    //  (obviously they are not new).
    if( event.isFromRefresh() )
      return;

    //  Take into account only processable files.

    if( isFileProcessable( file ))
    {
      //  Add file into the list for further confirmation only if the folder
      //  is not marked as UNKNOWN. In this case the file under that folder
      //  will be marked as unknown automatically.
      VirtualFile parent = file.getParent();
      if( parent != null )
      {
        FileStatus status = ChangeListManager.getInstance( project ).getStatus( parent );
        if( status != FileStatus.UNKNOWN )
        {
          VcsShowConfirmationOption option = host.getAddConfirmation();

          //  In the case when we need to perform "Add" vcs action right upon
          //  the file's creation, put the file into the host's cache until it
          //  will be analyzed by the ChangeProvider.
          if( option.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY )
            host.add2NewFile( path );
          else
          if( option.getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION )
          {
            List<VirtualFile> files = new ArrayList<VirtualFile>();
            files.add( file );

            AbstractVcsHelper helper = AbstractVcsHelper.getInstance( project );
            Collection<VirtualFile> filesToAdd =
              helper.selectFilesToProcess( files, TITLE, null, TITLE, MESSAGE, option );

            if( filesToAdd != null )
              host.add2NewFile( path );
          }
        }
      }
    }
  }

  public void beforeFileDeletion( VirtualFileEvent event )
  {
    @NonNls final String TITLE = "Delete file(s)";
    @NonNls final String MESSAGE = "Do you want to schedule the following file for deletion from Starteam?\n{0}";

    VirtualFile file = event.getFile();

    //  In the case of multi-vcs project configurations, we need to skip all
    //  notifications on non-owned files
    if( !VcsUtil.isFileForVcs( file, project, adapter ) )
      return;

    //  Do not ask user if the files created came from the vcs per se
    //  (obviously they are not new).
    if( event.isFromRefresh() )
      return;

    //  Do not ask anything if file is not versioned yet
    FileStatus status = FileStatusManager.getInstance( project ).getStatus( file );
    if( status == FileStatus.UNKNOWN || status == FileStatus.IGNORED )
      return;

    //  Take into account only processable files.
    if( isFileProcessable( file ) && VcsUtil.isFileForVcs( file, project, StarteamVcsAdapter.getInstance(project) ) )
    {
      if( status == FileStatus.ADDED )
      {
        host.deleteNewFile( file );
      }
      else
      {
        VcsShowConfirmationOption option = host.getDelConfirmation();

        //  In the case when we need to perform "Delete" vcs action right upon
        //  the file's creation, put the file into the host's cache until it
        //  will be analyzed by the ChangeProvider.
        if( option.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY )
        {
          deleteFileViaCheckinEnv( file );
        }
        else
        if( option.getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION )
        {
          List<VirtualFile> files = new ArrayList<VirtualFile>();
          files.add( file );

          AbstractVcsHelper helper = AbstractVcsHelper.getInstance( project );
          Collection<VirtualFile> filesToAdd =
            helper.selectFilesToProcess( files, TITLE, null, TITLE, MESSAGE, option );

          if( filesToAdd != null )
          {
            deleteFileViaCheckinEnv( file );
          }
          else
          {
            sheduleForRemove( file );
          }
        }
        else
        {
          sheduleForRemove( file );
        }
      }
    }
  }

  private void deleteFileViaCheckinEnv( VirtualFile file )
  {
    List<FilePath> list = new ArrayList<FilePath>();
    FilePath path = VcsContextFactory.SERVICE.getInstance().createFilePathOn( file );
    list.add( path );

    host.getCheckinEnvironment().scheduleMissingFileForDeletion( list );
  }
  
  private void sheduleForRemove( VirtualFile file )
  {
    String path = file.getPath();

    //  Trace only those files which are really reside in the
    //  repository and are needed in commands to the Starteam server.
    if( file.isDirectory() && host.existsFolder( path ) )
    {
      host.removedFoldersNameMap.put( file, path );
      markSubfolderStructure( path );
      host.removedFolders.add( path );
    }
    else
    if( host.existsFile( path ) )
      host.removedFiles.add( path );
  }

  /**
   * When adding new path into the list of the removed folders, remove from
   * that list all files/folders which were removed previously locating under
   * the given one (including it).
   */
  private void  markSubfolderStructure( String path )
  {
    for( Iterator<String> it = host.removedFiles.iterator(); it.hasNext(); )
    {
      String strFile = it.next();
      if( strFile.startsWith( path ) )
       it.remove();
    }
    for( Iterator<String> it = host.removedFolders.iterator(); it.hasNext(); )
    {
      String strFile = it.next();
      if( strFile.startsWith( path ) )
       it.remove();
    }
  }

  /**
   * File is not processable if it is outside the vcs scope or it is in the
   * list of excluded project files.
   */
  private boolean isFileProcessable( VirtualFile file )
  {
    return !host.isFileIgnored( file ) &&
           !FileTypeManager.getInstance().isFileIgnored( file.getName() );
  }
}
