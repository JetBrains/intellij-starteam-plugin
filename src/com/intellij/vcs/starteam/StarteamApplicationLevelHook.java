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
 * @author max
 */
package com.intellij.vcs.starteam;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class StarteamApplicationLevelHook implements ApplicationComponent {
  @NonNls private static final String SBAS_FOLDER_SIG = ".sbas;.IJI.*;";
  private final FileTypeManager myFileTypeManager;

  public StarteamApplicationLevelHook(final FileTypeManager fileTypeManager) {
    // explicit dependency to ensure correct initialization order
    myFileTypeManager = fileTypeManager;
  }

  public void disposeComponent() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Starteam.AppHook";
  }

  public void initComponent() {
    addIgnoredFolders();
  }

  /**
   * Automatically add ".sbas" folder into the list of ignored folders so that
   * they are not becoming the part of the project in the case when "Tools |
   * Personal Options | File | File Status Repository" is set to "Per Folder"
   */
  private void addIgnoredFolders()
  {
    String patterns = myFileTypeManager.getIgnoredFilesList();
    if( patterns.indexOf( SBAS_FOLDER_SIG ) == -1 )
    {
      final String newPattern = patterns + ((patterns.charAt( patterns.length() - 1 ) == ';') ? "" : ";" ) + SBAS_FOLDER_SIG;
      ApplicationManager.getApplication().runWriteAction( new Runnable()
        { public void run() { myFileTypeManager.setIgnoredFilesList( newPattern ); } }
      );
    }
  }
}
