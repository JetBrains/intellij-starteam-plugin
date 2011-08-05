/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 24.10.2006
 * Time: 19:42:21
 */
package com.intellij.vcs.starteam;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import com.intellij.vcsUtil.VcsUtil;
import com.starbase.starteam.Folder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class StarteamCheckinEnvironment implements CheckinEnvironment, RollbackEnvironment {
  private final Project project;
  private final StarteamVcs host;

  public StarteamCheckinEnvironment(final Project project, final StarteamVcs host) {
    this.project = project;
    this.host = host;
  }

  @Override
  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel checkinProjectPanel, PairConsumer<Object, Object> objectObjectPairConsumer) {
    return null;
  }


  @Nullable
  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpId() {
    return null;
  }

  public String getCheckinOperationName() {
    return VcsBundle.message("vcs.command.name.checkin");
  }

  public String getRollbackOperationName() {
    return VcsBundle.message("changes.action.rollback.text");
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    HashSet<FilePath> processedFiles = new HashSet<FilePath>();
    List<VcsException> errors = new ArrayList<VcsException>();
    List<String> mergeFiles = new ArrayList<String>();

    commitNew(changes, preparedComment, processedFiles, errors);
    commitChanged(changes, preparedComment, processedFiles, errors, mergeFiles);
    commitRenamed(changes, preparedComment, processedFiles, errors);

    VcsUtil.refreshFiles(project, processedFiles);

    if (mergeFiles.size() > 0) {
      final UpdatedFiles updatedFiles = UpdatedFiles.create();

      final VcsKey vcsKey = StarteamVcs.getKey();
      for (String file : mergeFiles) {
        updatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).add(file, vcsKey, null);
      }

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (project.isDisposed()) return;
          ProjectLevelVcsManager.getInstance(project)
            .showProjectOperationInfo(updatedFiles, StarteamBundle.message("local.vcs.action.name.checkin.files"));
        }
      });
    }
    return errors;
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment, @NotNull NullableFunction<Object, Object> parametersHolder) {
    return commit(changes, preparedComment);
  }

  private void commitNew(List<Change> changes, String comment, HashSet<FilePath> processedFiles, List<VcsException> errors) {
    HashSet<FilePath> folders = new HashSet<FilePath>();
    HashSet<FilePath> files = new HashSet<FilePath>();

    collectNewFilesAndFolders(changes, processedFiles, folders, files);
    commitFoldersAndFiles(folders, files, comment, errors);
  }

  private void collectNewFilesAndFolders(List<Change> changes,
                                         HashSet<FilePath> processedFiles,
                                         HashSet<FilePath> folders,
                                         HashSet<FilePath> files) {
    for (Change change : changes) {
      if (VcsUtil.isChangeForNew(change)) {
        FilePath filePath = change.getAfterRevision().getFile();
        if (filePath.isDirectory()) {
          folders.add(filePath);
        }
        else {
          files.add(filePath);
          analyzeParent(filePath, folders);
        }
      }
    }
    processedFiles.addAll(folders);
    processedFiles.addAll(files);
  }

  /**
   * Add all folders first, then add all files into these folders.
   * Difference between added and modified files is that added file
   * has no "before" revision.
   */
  private void commitFoldersAndFiles(HashSet<FilePath> folders, HashSet<FilePath> files, String comment, List<VcsException> errors) {
    FilePath[] foldersSorted = folders.toArray(new FilePath[folders.size()]);
    foldersSorted = VcsUtil.sortPathsFromOutermost(foldersSorted);

    for (FilePath folder : foldersSorted) {
      try {
        String parentPath = StarteamChangeProvider.getSTCanonicPath(folder.getVirtualFileParent().getPath());
        host.addDirectory(parentPath, folder.getName(), comment);
      }
      catch (VcsException e) {
        errors.add(e);
      }
    }

    for (FilePath file : files) {
      try {
        String parentPath = StarteamChangeProvider.getSTCanonicPath(file.getVirtualFileParent().getPath());
        host.addFile(parentPath, file.getName(), comment, null);
      }
      catch (VcsException e) {
        errors.add(e);
      }
    }
  }

  /**
   * If the parent of the file has status New or Unversioned - add it
   * to the list of folders OBLIGATORY for addition into the repository -
   * no file can be added into VSS without all higher folders are already
   * presented there.
   * Process with the parent's parent recursively.
   */
  private void analyzeParent(FilePath file, HashSet<FilePath> folders) {
    VirtualFile parent = file.getVirtualFileParent();
    FileStatus status = FileStatusManager.getInstance(project).getStatus(parent);
    if (status == FileStatus.ADDED || status == FileStatus.UNKNOWN) {
      FilePath parentPath = file.getParentPath();
      folders.add(parentPath);
      analyzeParent(parentPath, folders);
    }
  }

  private void commitChanged(List<Change> changes,
                             String preparedComment,
                             HashSet<FilePath> processedFiles,
                             List<VcsException> errors,
                             List<String> mergeFiles) {
    for (Change change : changes) {
      try {
        //noinspection ConstantConditions
        FilePath file = change.getAfterRevision().getFile();
        if (!VcsUtil.isRenameChange(change) && (change.getBeforeRevision() != null)) {
          String starteamFilePath = StarteamChangeProvider.getSTCanonicPath(file);
          boolean success = host.checkinFile(starteamFilePath, preparedComment, null);
          if (!success) mergeFiles.add(starteamFilePath);
        }
        processedFiles.add(file);
      }
      catch (VcsException e) {
        errors.add(e);
      }
    }
  }

  private void commitRenamed(List<Change> changes, String preparedComment, HashSet<FilePath> processedFiles, List<VcsException> errors) {
    for (Change change : changes) {
      try {
        if (VcsUtil.isRenameChange(change)) {
          FilePath file = change.getAfterRevision().getFile();
          String newPath = file.getPath();
          String oldPath = StarteamChangeProvider.getSTCanonicPath(change.getBeforeRevision().getFile());
          if (file.isDirectory()) {
            host.renameDirectoryNew(StarteamChangeProvider.getSTCanonicPath(newPath), file.getName());
            host.renamedDirs.remove(newPath);
          }
          else {
            //  If parent folders' names of the revisions coinside, then we
            //  deal with the simple rename, otherwise we process full-scaled
            //  file movement across folders (packages).

            FilePath oldfile = change.getBeforeRevision().getFile();
            if (oldfile.getVirtualFileParent().getPath().equals(file.getVirtualFileParent().getPath())) {
              host.renameAndCheckInFile(oldPath, file.getName(), preparedComment);
            }
            else {
              String newFolder = StarteamChangeProvider.getSTCanonicPath(file.getVirtualFileParent().getPath());
              host.moveRenameAndCheckInFile(oldPath, newFolder, file.getName(), preparedComment);
            }

            host.renamedFiles.remove(newPath);
            processedFiles.add(file);
          }
        }
      }
      catch (VcsException e) {
        errors.add(e);
      }
    }
  }

  /**
   * Rollback of changes made is performed by simple override of the
   * current files with "CheckOut" command with keeping the file r/w
   * status.
   */
  public void rollbackChanges(List<Change> changes, final List<VcsException> errors, @NotNull final RollbackProgressListener listener) {
    HashSet<FilePath> processedFiles = new HashSet<FilePath>();

    listener.determinate();
    rollbackNew(changes, processedFiles, listener);
    rollbackChanged(changes, errors, listener);
  }

  private void rollbackNew(List<Change> changes, HashSet<FilePath> processedFiles, @NotNull final RollbackProgressListener listener) {
    HashSet<FilePath> filesAndFolder = new HashSet<FilePath>();
    collectNewChangesBack(changes, filesAndFolder, processedFiles);

    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
    for (FilePath file : filesAndFolder) {
      listener.accept(file);
      host.deleteNewFile(file.getPath());
      mgr.fileDirty(file);
    }
  }

  /**
   * For each accumulated (to be rolledback) folder - collect ALL files
   * in the change lists with the status NEW (ADDED) which are UNDER this folder.
   * This ensures that no file will be left in any change list with status NEW.
   */
  private void collectNewChangesBack(List<Change> changes, HashSet<FilePath> newFilesAndfolders, HashSet<FilePath> processedFiles) {
    HashSet<FilePath> foldersNew = new HashSet<FilePath>();
    for (Change change : changes) {
      if (VcsUtil.isChangeForNew(change)) {
        FilePath filePath = change.getAfterRevision().getFile();
        if (!filePath.isDirectory()) {
          newFilesAndfolders.add(filePath);
        }
        else {
          foldersNew.add(filePath);
        }
        processedFiles.add(filePath);
      }
    }

    ChangeListManager clMgr = ChangeListManager.getInstance(project);
    FileStatusManager fsMgr = FileStatusManager.getInstance(project);
    List<VirtualFile> allAffectedFiles = clMgr.getAffectedFiles();

    for (VirtualFile file : allAffectedFiles) {
      FileStatus status = fsMgr.getStatus(file);
      if (status == FileStatus.ADDED) {
        for (FilePath folder : foldersNew) {
          if (file.getPath().toLowerCase().startsWith(folder.getPath().toLowerCase())) {
            FilePath path = clMgr.getChange(file).getAfterRevision().getFile();
            newFilesAndfolders.add(path);
          }
        }
      }
    }
    newFilesAndfolders.addAll(foldersNew);
  }

  /**
   * Rolling back modified files is a getting out the latest copy of them
   * from the repository. The only difference in the processing is made for
   * renamed files - we must get out file with the original name.
   */
  private void rollbackChanged(List<Change> changes, List<VcsException> errors, @NotNull final RollbackProgressListener listener) {
    for (Change change : changes) {
      FilePath newFile = change.getAfterRevision().getFile();
      String newPath = StarteamChangeProvider.getSTCanonicPath(newFile);
      try {
        if (VcsUtil.isRenameChange(change)) {
          listener.accept(change);

          FilePath oldFile = change.getBeforeRevision().getFile();
          String oldPath = StarteamChangeProvider.getSTCanonicPath(oldFile);

          if (newFile.isDirectory()) {
            new File(newPath).renameTo(new File(oldPath));
            host.setWorkingFolderName(newPath, oldFile.getName());
            host.renamedDirs.remove(newFile.getPath());
          }
          else {
            host.checkoutFile(oldPath, false);
            host.renamedFiles.remove(newFile.getPath());

            FileUtil.delete(new File(newPath));
          }
        }
        else if (!VcsUtil.isChangeForNew(change)) {
          listener.accept(change);

          host.checkoutFile(newPath, false);
        }
      }
      catch (VcsException e) {
        errors.add(e);
      }
    }
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
    List<File> ioFiles = ChangesUtil.filePathsToFiles(files);

    //  First, remove all ordinary files and only then folders in order not to
    //  deal with mutual subordering.
    removeItems(ioFiles, false);
    removeItems(ioFiles, true);
    return new ArrayList<VcsException>();
  }

  private void removeItems(List<File> files, boolean isDir) {
    for (File file : files) {
      String starteamPath = StarteamChangeProvider.getSTCanonicPath(file.getPath());
      Folder folder = host.findFolder(starteamPath);
      if ((folder != null) == isDir) {
        if (folder != null) {
          folder.remove();

          String ignoredPath = starteamPath.substring(0, file.getParentFile().getPath().length()) + File.separatorChar + StarteamVcs
            .RENAMED_FOLDER_PREFIX + file.getName();
          FileUtil.delete(new File(ignoredPath));
        }
        else {
          com.starbase.starteam.File starteamFile = host.findFile(starteamPath);
          if (starteamFile != null) starteamFile.remove();
        }

        String canonicPath = file.getPath().replace(File.separatorChar, '/');
        host.removedFolders.remove(canonicPath);
        host.removedFiles.remove(canonicPath);
      }
    }
  }

  public void rollbackMissingFileDeletion(List<FilePath> files, final List<VcsException> exceptions,
                                                        final RollbackProgressListener listener) {
    List<File> ioFiles = ChangesUtil.filePathsToFiles(files);
    //  First, unremove all folders and only then files
    unremoveItems(ioFiles, true, exceptions, listener);
    unremoveItems(ioFiles, false, exceptions, listener);
  }

  private void unremoveItems(List<File> files, boolean isDir, List<VcsException> errors, final RollbackProgressListener listener) {
    for (File file : files) {
      try {
        String starteamPath = StarteamChangeProvider.getSTCanonicPath(file.getPath());
        String canonicPath = file.getPath().replace(File.separatorChar, '/');

        Folder folder = host.findFolder(starteamPath);
        if ((folder != null) == isDir) {
          listener.accept(file);
          if (folder != null) {
            String ignoredPath = starteamPath.substring(0, file.getParentFile().getPath().length()) + File.separatorChar + StarteamVcs
              .RENAMED_FOLDER_PREFIX + file.getName();
            host.removedFolders.remove(canonicPath);
            FileUtil.rename(new File(ignoredPath), file);
          }
          else {
            com.starbase.starteam.File starteamFile = host.findFile(starteamPath);
            if (starteamFile != null) host.checkoutFile(starteamFile, false);

            host.removedFiles.remove(canonicPath);
          }
        }
      }
      catch (VcsException e) {
        errors.add(e);
      }
      catch (IOException e) {
        errors.add(new VcsException(e));
      }
    }
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    for (VirtualFile file : files) {
      host.add2NewFile(file.getPath());
      VcsUtil.markFileAsDirty(project, file);

      //  Extend status change to all parent folders if they are not
      //  included into the context of the menu action.
      extendStatus(file);
    }
    // Keep intentionally empty.
    return new ArrayList<VcsException>();
  }

  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

  private void extendStatus(VirtualFile file) {
    FileStatusManager mgr = FileStatusManager.getInstance(project);
    VirtualFile parent = file.getParent();

    if (mgr.getStatus(parent) == FileStatus.UNKNOWN) {
      host.add2NewFile(parent);
      VcsUtil.markFileAsDirty(project, parent);

      extendStatus(parent);
    }
  }

  public void rollbackModifiedWithoutCheckout(final List<VirtualFile> files, final List<VcsException> exceptions,
                                                            final RollbackProgressListener listener) {
    throw new UnsupportedOperationException();
  }

  public void rollbackIfUnchanged(VirtualFile file) {
  }
}
