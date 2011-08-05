package com.intellij.vcs.starteam.actions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.starteam.StarteamBundle;
import com.intellij.vcs.starteam.StarteamVcs;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author mike
 */
public class ShowDiffAction extends BasicAction
{
  private static final Logger LOG = Logger.getInstance("#com.intellij.vcs.starteam.actions.ShowDiffAction");

  private static byte[] getContentOf(VirtualFile file) throws IOException {
    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document == null)
      return file.contentsToByteArray();
    return document.getText().getBytes(file.getCharset().name());
  }

  protected void perform(Project project, StarteamVcs activeVcs, VirtualFile file) throws VcsException
  {
    try {
      byte[] localContent = getContentOf(file);
      String upToDateFilePath = file.getPresentableUrl();
      final byte[] vcsContent = activeVcs.getFileContent(upToDateFilePath);
      if (vcsContent == null) return;

      /*
      final Object modalContext = context.getData(DataConstants.IS_MODAL_CONTEXT);
      Object modalHint = modalContext != null && modalContext.equals(Boolean.TRUE) ?
                         DiffTool.HINT_SHOW_MODAL_DIALOG : DiffTool.HINT_SHOW_FRAME;
      */
      Object modalHint = DiffTool.HINT_SHOW_MODAL_DIALOG;

      FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);

      if (fileType.isBinary()){
        if (Arrays.equals(localContent, vcsContent)){
          Messages.showMessageDialog(StarteamBundle.message("message.text.diff.binary.contents.equal"), StarteamBundle.message("message.title.diff.contents.equal"), Messages.getInformationIcon());
        } else {
          Messages.showMessageDialog(StarteamBundle.message("message.text.diff.binary.contents.different"), StarteamBundle.message("message.title.contents.different"), Messages.getInformationIcon());
        }
        return;
      }

      BinaryContent content = new BinaryContent(vcsContent, file.getCharset(), fileType);
      SimpleDiffRequest diffRequest = new SimpleDiffRequest(project, StarteamBundle.message("diff.content.title.file.history",
                                                                                            file.getPresentableUrl()));
      diffRequest.setContentTitles(StarteamBundle.message("diff.content.title.repository.version", activeVcs.getDisplayName()), StarteamBundle.message("diff.content.title.local.version"));
      diffRequest.setContents(content, new FileContent(project, file));
      diffRequest.addHint(modalHint);
      DiffTool diffTool = DiffManager.getInstance().getDiffTool();
      if (diffTool.canShow(diffRequest)) diffTool.show(diffRequest);

    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  protected boolean isEnabled(Project project, AbstractVcs vcs, VirtualFile file) {
    return !file.isDirectory() && FileStatusManager.getInstance(project).getStatus(file) != FileStatus.ADDED;
  }

  protected String getActionName() {
    return null;
  }
}
