/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Oct 23, 2002
 * Time: 3:08:34 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.vcs.starteam;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class StarteamVcsAdapter extends AbstractVcs implements ProjectComponent, JDOMExternalizable
{
  @NonNls private static final String PERSISTENCY_REMOVED_TAG = "StarbasePersistencyRemovedFile";
  @NonNls private static final String PERSISTENCY_RENAMED_TAG = "StarbasePersistencyRenamedFile";
  @NonNls private static final String PERSISTENCY_NEW_FILE_TAG = "StarbasePersistencyNewFile";
  @NonNls private static final String PATH_DELIMITER = "%%%";

  private StarteamVcs myStarteamVcs;
  private final StarteamConfiguration config;

  public StarteamVcsAdapter(Project project, StarteamConfiguration starteamConfiguration)
  {
    super(project, StarteamVcs.NAME);
    config = starteamConfiguration;
  }

  public StarteamVcs getStarteamVcs() {  return myStarteamVcs;  }

  public String getDisplayName()  {  return "StarTeam";  }
  @NotNull
  public String getComponentName(){  return "StarteamVcsAdapter";  }

  public void projectClosed() {
    if (getStarteamVcs() != null) getStarteamVcs().projectClosed();
  }

  public void projectOpened() {
    if (getStarteamVcs() != null) getStarteamVcs().projectOpened();
  }

  @Override
  protected void start() throws VcsException {
    // no 'start' any more, but - init vcs
    try
    {
      Class.forName("com.starbase.starteam.Project");
      myStarteamVcs = new StarteamVcs( myProject, config );
    }
    catch (Throwable e) {
      //  Nothing to do - if "starteam.Project" class has not been
      //  referred propely, myStarteamVcs will be NULL.
      throw new VcsException(StarteamBundle.message( "exception.text.configuration.cant.start.classes.not.found",
                                                     ApplicationNamesInfo.getInstance().getProductName()) + e.getMessage() );
    }
  }

  public void activate() {
    if (getStarteamVcs() != null) getStarteamVcs().activate();
  }

  public void deactivate() {
    if (getStarteamVcs() != null) getStarteamVcs().deactivate();
  }

  public void disposeComponent() {
    if (getStarteamVcs() != null) getStarteamVcs().disposeComponent();
  }

  public void initComponent() {}

  public Configurable getConfigurable() {
    return (getStarteamVcs() != null) ? getStarteamVcs().getConfigurable() : new MyConfigurable();
  }

  public static StarteamVcsAdapter getInstance(Project project) {
    return project.getComponent(StarteamVcsAdapter.class);
  }

  public String getMenuItemText() {  return StarteamBundle.message("starteam.menu.group.text");  }

  @Nullable
  public CheckinEnvironment getCheckinEnvironment(){
    return (getStarteamVcs() != null) ? getStarteamVcs().getCheckinEnvironment() : null;
  }

  @Nullable
  public RollbackEnvironment getRollbackEnvironment(){
    return (getStarteamVcs() != null) ? getStarteamVcs().getRollbackEnvironment() : null;
  }
  @Nullable
  public EditFileProvider getEditFileProvider(){
    return (getStarteamVcs() != null) ? getStarteamVcs().getEditFileProvider() : null;
  }

  @Nullable
  public UpdateEnvironment getUpdateEnvironment(){
    return (getStarteamVcs() != null) ? getStarteamVcs().getUpdateEnvironment() : null;
  }

  @Nullable
  public UpdateEnvironment getStatusEnvironment(){
    return (getStarteamVcs() != null) ? getStarteamVcs().getStatusEnvironment() : null;
  }

  @Nullable
  public ChangeProvider getChangeProvider(){
    return (getStarteamVcs() != null) ? getStarteamVcs().getChangeProvider() : null;
  }

  @Nullable
  public VcsHistoryProvider getVcsHistoryProvider(){
    return (getStarteamVcs() != null) ? getStarteamVcs().getVcsHistoryProvider() : null;
  }

  public void loadSettings() {
    super.loadSettings();
    if (getStarteamVcs() != null) {
      getStarteamVcs().loadSettings();
    }
  }

  public boolean isVersionedDirectory( VirtualFile dir )
  {
    return (getStarteamVcs() != null) ? getStarteamVcs().isVersionedDirectory( dir ) : false; 
  }

  //
  // JDOMExternalizable methods
  //
  public void readExternal(final Element element) throws InvalidDataException
  {
    StarteamVcs host = getStarteamVcs();
    if( host != null )
    {
      List files = element.getChildren( PERSISTENCY_REMOVED_TAG );
      for (Object cclObj : files)
      {
        if (cclObj instanceof Element)
        {
          final Element currentCLElement = ((Element)cclObj);
          final String path = currentCLElement.getValue();

          // Safety check - file can be added again between IDE sessions.
          if( ! new File( path ).exists() )
            myStarteamVcs.removedFiles.add( path );
        }
      }

      files = element.getChildren( PERSISTENCY_RENAMED_TAG );
      for (Object cclObj : files)
      {
        if (cclObj instanceof Element)
        {
          final Element currentCLElement = ((Element)cclObj);
          final String pathPair = currentCLElement.getValue();
          int delimIndex = pathPair.indexOf( PATH_DELIMITER );
          if( delimIndex != -1 )
          {
            final String newName = pathPair.substring( 0, delimIndex );
            final String oldName = pathPair.substring( delimIndex + PATH_DELIMITER.length() );

            // Safety check - file can be deleted or changed between IDE sessions.
            if( new File( newName ).exists() )
              myStarteamVcs.renamedFiles.put( newName, oldName );
          }
        }
      }

      files = element.getChildren( PERSISTENCY_NEW_FILE_TAG );
      for (Object cclObj : files)
      {
        if (cclObj instanceof Element)
        {
          final Element currentCLElement = ((Element)cclObj);
          final String path = currentCLElement.getValue();

          // Safety check - file can be deleted or changed between IDE sessions.
          if( new File( path ).exists() )
            host.newFiles.add( path.toLowerCase() );
        }
      }
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException
  {
    StarteamVcs host = getStarteamVcs();
    if( host != null )
    {
      writeExternalElement( element, host.removedFiles, PERSISTENCY_REMOVED_TAG );
      writeExternalElement( element, host.removedFolders, PERSISTENCY_REMOVED_TAG );
      writeExternalElement( element, host.newFiles, PERSISTENCY_NEW_FILE_TAG );

      for( String file : host.renamedFiles.keySet() )
      {
        final Element listElement = new Element( PERSISTENCY_RENAMED_TAG );
        final String pathPair = file.concat( PATH_DELIMITER ).concat( host.renamedFiles.get( file ) );

        listElement.addContent( pathPair );
        element.addContent( listElement );
      }
    }
  }

  private static void writeExternalElement( final Element element, HashSet<String> files, String tag )
  {
    //  Sort elements of the list so that there is no perturbation in .ipr/.iml
    //  files in the case when no data has changed.
    String[] sorted = ArrayUtil.toStringArray(files);
    Arrays.sort( sorted );

    for( String file : sorted )
    {
      final Element listElement = new Element( tag );
      listElement.addContent( file );
      element.addContent( listElement );
    }
  }

  public static class MyConfigurable implements Configurable
  {
    public String getDisplayName() {  return null;  }
    public Icon   getIcon()        {  return null;  }
    public String getHelpTopic()   {  return null;  }

    public JComponent createComponent()
    {
      final JPanel result = new JPanel(new BorderLayout());
      result.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      result.add(new JLabel(StarteamBundle.message("label.configuration.starteam.jar.not.found", File.separator,
                                                   PathManager.getLibPath().replace('/', File.separatorChar),
                                                   ApplicationNamesInfo.getInstance().getProductName())), BorderLayout.NORTH);
      return result;
    }

    public boolean isModified() {  return false;   }
    public void apply() throws ConfigurationException { }
    public void reset() {}
    public void disposeUIResources() {}
  }
}
