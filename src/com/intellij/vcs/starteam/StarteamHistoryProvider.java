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

package com.intellij.vcs.starteam;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.util.ui.ColumnInfo;
import com.starbase.starteam.File;
import com.starbase.starteam.Item;
import com.starbase.starteam.User;
import com.starbase.starteam.vts.comm.CommandException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Nov 15, 2006
 */
public class StarteamHistoryProvider implements VcsHistoryProvider
{
  private final StarteamVcs host;

  public StarteamHistoryProvider( StarteamVcs host )
  {
    this.host = host;
  }

  @NonNls
  @Nullable
  public String getHelpId() {  return null;  }

  public boolean supportsHistoryForDirectories() {
    return false;
  }

  public VcsDependentHistoryComponents getUICustomization(final VcsHistorySession session, JComponent forShortcutRegistration) {  return VcsDependentHistoryComponents.createOnlyColumns(new ColumnInfo[0]);   }
  public AnAction[] getAdditionalActions(final Runnable refresher) {  return new AnAction[0];   }
  public boolean isDateOmittable() {  return false;  }

  public VcsHistorySession createSessionFor( FilePath filePath ) throws VcsException
  {
    final File file;
    try
    {
      file = host.findFile( filePath.getPath() );
    }
    catch( CommandException e )
    {
      throw new VcsException( e );
    }

    if( file != null )
    {
      Item[] items = file.getHistory();
      ArrayList<VcsFileRevision> revisions = new ArrayList<VcsFileRevision>();

      for( Item item : items )
      {
        VcsFileRevision rev = new StarteamFileRevision( item );
        revisions.add( rev );
      }
      return new StarteamHistorySession(revisions, file);
    }
    else
    {
      throw new VcsException( "Can not find file: " + filePath.getPath() );
    }
  }

  public void reportAppendableHistory(FilePath path, VcsAppendableHistorySessionPartner partner) throws VcsException {
    final VcsHistorySession session = createSessionFor(path);
    partner.reportCreatedEmptySession((VcsAbstractHistorySession) session);
  }

  private static VcsRevisionNumber getCurrentRevisionNum( File file )
  {
    VcsRevisionNumber revNum = VcsRevisionNumber.NULL;
    try
    {
      Item[] items = file.getHistory();
      if( items.length > 0 )
        revNum = new VcsRevisionNumber.Int( items[ items.length - 1 ].getRevisionNumber() + 1 );
    }
    catch( Exception e )
    {
      //  We can catch e.g. com.starbase.starteam.ItemNotFoundException if we
      //  try to show history records for the deleted file.
      revNum = VcsRevisionNumber.NULL;
    }
    return revNum;
  }

    private static class StarteamHistorySession extends VcsAbstractHistorySession {
      private final File file;

      public StarteamHistorySession(List<VcsFileRevision> revisions, File file) {
        super(revisions);
        this.file = file;
      }

      @Nullable
      public VcsRevisionNumber calcCurrentRevisionNumber() {
        return getCurrentRevisionNum(file);
      }

      public HistoryAsTreeProvider getHistoryAsTreeProvider() {
        return null;
      }

        @Override
        public VcsHistorySession copy() {
          return new StarteamHistorySession(getRevisionList(), file);
        }
    }

    private class StarteamFileRevision implements VcsFileRevision
  {
    private final Item item;
    private byte[] contents = null;

    public StarteamFileRevision( Item item )
    {
      this.item = item;
    }

    public VcsRevisionNumber getRevisionNumber() { return new VcsRevisionNumber.Int( item.getRevisionNumber() + 1 ); }
    @Nullable
    public String getBranchName() { return null; }
    public Date   getRevisionDate() { return new Date( item.getModifiedTime().getLongValue() ); }
    public String getCommitMessage() { return item.getComment(); }
    public String getAuthor()
    {
      String userName = StarteamBundle.message( "unknown.author.name" );
      try
      {
        User user = item.getServer().getUser( item.getModifiedBy() );
        userName = user.getName();
      }
      catch( Exception e ){
        //  Nothing to do - try/catch here is to overcome internal Starteam SDK
        //  problem - it throws NPE inside <server.getUser(int)>.
      }
      return userName;
    }

    public void loadContent() throws VcsException
    {
      if( item instanceof File && contents == null )
      {
        try
        {
          File stFile = (File)item;

          java.io.File file = new java.io.File(
            FileUtil.getTempDirectory() + java.io.File.separator + Long.toString(System.currentTimeMillis()) + stFile.getName());
          stFile.checkoutTo(file, Item.LockType.UNCHANGED, true, true, false);
          contents = FileUtil.loadFileBytes( file );
        }
        catch( IOException e )
        {
          throw new VcsException( e );
        }
      }
    }

    public byte[] getContent() { return contents; }

    public int compareTo( Object revision )
    {
      return getRevisionDate().compareTo( ((VcsFileRevision)revision).getRevisionDate() );
    }
  }
}

