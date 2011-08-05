package com.intellij.vcs.starteam.actions;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.starteam.StarteamBundle;
import com.intellij.vcs.starteam.StarteamVcs;
import com.intellij.vcs.starteam.StarteamVcsAdapter;
import com.intellij.vcsUtil.VcsUtil;

import java.util.List;

/**
 * @author mike
 */
public abstract class BasicAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final VirtualFile[] files = VcsUtil.getVirtualFiles(e);
    AbstractVcs starteamVcs = null;
    try {
      starteamVcs = StarteamVcs.getInstance(project);
    }
    catch (NoClassDefFoundError exc) {
      Messages.showErrorDialog(project, StarteamBundle.message("message.text.lost.connection"),
                               StarteamBundle.message("message.title.operation.failed.error"));
    }

    //  Take into account (...==null) ANY problem with StarteamVCS state -
    //  absence of starteam jdk, timeouts, etc.
    if (files.length == 0 || starteamVcs == null) return;

    if (!ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(starteamVcs, files)) {
      FileDocumentManager.getInstance().saveAllDocuments();
    }

    final String actionName = getActionName();

    AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
    LocalHistoryAction action = LocalHistory.getInstance().startAction(actionName);

    try {
      final AbstractVcs host = starteamVcs;
      List exceptions = helper.runTransactionRunnable(starteamVcs, new TransactionRunnable() {
        public void run(List exceptions) {
          for (VirtualFile file : files) {
            try {
              execute(project, host, file);
            }
            catch (VcsException ex) {
              ex.setVirtualFile(file);
              exceptions.add(ex);
            }
          }
        }
      }, null);

      helper.showErrors(exceptions, actionName != null ? actionName : starteamVcs.getDisplayName());
    }
    finally {
      action.finish();
    }
  }

  public void update(AnActionEvent e) {
    super.update(e);
    updateStarteamAction(e, this);
  }

  public static void updateStarteamAction(AnActionEvent e, BasicAction action) {
    Presentation presentation = e.getPresentation();

    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    VirtualFile[] files = VcsUtil.getVirtualFiles(e);
    if (files.length == 0) {
      presentation.setEnabled(false);
      presentation.setVisible(true);
      return;
    }

    for (VirtualFile file : files) {
      final AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
      if (activeVcs == null || !(activeVcs instanceof StarteamVcsAdapter)) {
        presentation.setEnabled(false);
        presentation.setVisible(false);
        return;
      }

      if (action != null) {
        if (!action.isEnabled(project, activeVcs, file)) {
          presentation.setEnabled(false);
          return;
        }
      }
    }
  }


  private void execute(final Project project, final AbstractVcs activeVcs, final VirtualFile file) throws VcsException {
    final VcsException[] e = new VcsException[1];

    try {
      performOnItem(project, activeVcs, file);
    }
    catch (VcsException exc) {
      e[0] = exc;
    }

    if (file.isDirectory() && e[0] != null) {
      //  Iterate over only those files which are actually the part of the
      //  project structure. Do not touch the whole underlying directory
      //  structure since there can be numerous auxiliary folders like ".sbas"
      //  which should be skipped.

      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      fileIndex.iterateContentUnderDirectory(file, new ContentIterator() {
        public boolean processFile(VirtualFile itFile) {
          //  Perverse way to store the exception from the anonymous ContentIterator
          //  implementor.
          try {
            performOnItem(project, activeVcs, file);
          }
          catch (VcsException exc) {
            //  Store only the first exception to keep track the earliet problem.
            //  But try to iterate over the whole subproject tree, e.g. to eliminate
            //  possible single mistake.
            if (e[0] == null) e[0] = exc;
          }

          return true;
        }
      });
    }

    //  Rethrow the accumulated exception.
    if (e[0] != null) throw e[0];
  }

  private void performOnItem(final Project project, final AbstractVcs activeVcs, final VirtualFile file) throws VcsException {
    perform(project, (StarteamVcs)activeVcs, file);
    VcsDirtyScopeManager.getInstance(project).fileDirty(file);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        file.refresh(false, true);
      }
    });
  }

  protected abstract String getActionName();

  protected abstract boolean isEnabled(Project project, AbstractVcs vcs, VirtualFile file);

  protected abstract void perform(Project project, final StarteamVcs activeVcs, VirtualFile file) throws VcsException;
}
