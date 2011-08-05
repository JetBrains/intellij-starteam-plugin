package com.intellij.vcs.starteam;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.update.*;
import com.intellij.openapi.util.Ref;
import com.starbase.starteam.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: May 5, 2006
 */
public class StarteamUpdateEnvironment implements UpdateEnvironment
{
  private final StarteamVcs host;
  private ProgressIndicator progressIndicator;
  private UpdatedFiles groups;
  private int  iteratedFodersCount;

  public StarteamUpdateEnvironment( StarteamVcs vcs ) {  host = vcs;   }

  public void fillGroups( UpdatedFiles groups ) {}

  @Nullable
  public Configurable createConfigurable( Collection<FilePath> files ) {  return null;  }

  @NotNull
  @SuppressWarnings({"ThrowableInstanceNeverThrown"})
  public UpdateSession updateDirectories(@NotNull FilePath[] roots, UpdatedFiles updatedFiles, ProgressIndicator progress, @NotNull final Ref<SequentialUpdatesContext> context) throws ProcessCanceledException
  {
    final ArrayList<VcsException> errors = new ArrayList<VcsException>();

    progressIndicator = progress;
    groups = updatedFiles;
    iteratedFodersCount = 0;
    
    refreshHost( errors );

    try
    {
      for( FilePath path : roots )
      {
        Folder folder = host.findFolder( path.getPath() );
        if( folder != null )
        {
          processStarteamFolder( folder, errors );
        }
      }
    }
    catch( SocketException e ){  errors.add( new VcsException( e.getMessage() ) );  }
    catch( ServerException e ){  errors.add( new VcsException( e.getMessage() ) );  }
    catch( TypeNotFoundException e ){  errors.add( new VcsException( StarteamBundle.message("message.text.expired.license") ) );  }

    return new UpdateSession(){
      @NotNull
      public List<VcsException> getExceptions() {  return errors;  }
      public void onRefreshFilesCompleted()     {}
      public boolean isCanceled()               {  return false;   }
    };
  }

  private void  processStarteamFolder( Folder folder, ArrayList<VcsException> errors ) throws SocketException
  {
    //  Exclude folders which are not modules under VCS.
    if( folder != null )
    {
      iteratedFodersCount++;
      if( iteratedFodersCount % 10 == 0 && progressIndicator != null )
      {
        progressIndicator.setText( StarteamBundle.message("update.progress.prefix") + " " +
                                   iteratedFodersCount + StarteamBundle.message("update.progress.suffix") );
      }

      //  If the folder is new for local project - create it.
      java.io.File checkFolder = new java.io.File( folder.getPath() );
      if( !checkFolder.exists() )
      {
        checkFolder.mkdir();
      }

      //  We have always to refresh folder's status in order to correctly
      //  reflex the changes in repository.
      folder.update();
      host.refreshFolder( folder );

      File[] files = host.getFiles( folder );
      for( File file : files )
        processFile( file, errors );

      Folder[] subFolders = host.getSubFolders( folder );
      for( Folder subFolder : subFolders )
        processStarteamFolder( subFolder, errors );
    }
  }

  private void  processFile( File file, ArrayList<VcsException> errors )
  {
    final VcsKey vcsKey = StarteamVcs.getKey();
    try
    {
      int status = file.getStatus();
      if( status == Status.MISSING || status == Status.OUTOFDATE )
      {
        host.checkoutFile( file, false );
        groups.getGroupById( FileGroup.UPDATED_ID ).add(file.getFullName(), vcsKey, null);
      }
      else
      if( status == Status.MODIFIED  )
      {
        groups.getGroupById( FileGroup.SKIPPED_ID ).add(file.getFullName(), vcsKey, null);
      }
      else
      if( status == Status.MERGE  )
      {
        groups.getGroupById( FileGroup.MERGED_WITH_CONFLICT_ID ).add(file.getFullName(), vcsKey, null);
      }
    }
    catch( IOException e )
    {
      errors.add( new VcsException( e ) );
    }
    catch( VcsException e )
    {
      errors.add( new VcsException( e ) );
    }
  }

  private void refreshHost(final ArrayList<VcsException> errors)
  {
    try {  host.refresh();  }
    catch( VcsException e ) {  errors.add( e );   }
  }

  public boolean validateOptions(final Collection<FilePath> roots) {
    return true;
  }
}
