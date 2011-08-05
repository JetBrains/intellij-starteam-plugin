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

package com.intellij.vcs.starteam.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.starteam.StarteamBundle;
import com.intellij.vcs.starteam.StarteamVcs;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Sep 20, 2006
 * Time: 4:06:50 PM
 * To change this template use File | Settings | File Templates.
 */
public class UpdateStatusAction extends BasicAction
{
  protected String getActionName() {  return "UpdateStatus";  }

  protected boolean isEnabled(Project project, AbstractVcs vcs, VirtualFile file) {
    return true;
  }

  protected void perform( final Project project, StarteamVcs activeVcs, VirtualFile file )
  {
    try{
      activeVcs.refresh();
      activeVcs.updateStatus( file );
    }
    catch(VcsException ex){
      Messages.showMessageDialog(project, ex.getMessage(), StarteamBundle.message("message.title.action.error"), Messages.getErrorIcon());
    }
    catch(IOException ex){
      Messages.showMessageDialog(project, ex.getMessage(), StarteamBundle.message("message.title.action.error"), Messages.getErrorIcon());
    }
  }
}
