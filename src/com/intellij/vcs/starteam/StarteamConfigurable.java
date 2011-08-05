package com.intellij.vcs.starteam;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.starbase.starteam.Project;
import com.starbase.starteam.Server;
import com.starbase.starteam.View;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author mike
 */
public class StarteamConfigurable implements Configurable
{
  private static final Logger LOG = Logger.getInstance("#com.intellij.vcs.starteam.StarteamConfigurable");

  JPanel myPanel;
  private JTextField myFldServer;
  private JTextField myFldPort;
  private JTextField myFldUser;
  private JPasswordField myFldPassword;
  private TextFieldWithBrowseButton myFldProject;
  private TextFieldWithBrowseButton myFldView;
  private JButton myBtnTest;
  private final com.intellij.openapi.project.Project myProject;
  private TextFieldWithBrowseButton myFldWorkingPath;
  private JPanel optionsPanel;
  private JCheckBox myCheckLockOnCheckout;
  private JCheckBox myCheckUnlockOnCheckin;
  private File myLastChosenDirectory;

  public StarteamConfigurable(com.intellij.openapi.project.Project project ) {
    myProject = project;
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  @Nullable
  public String getDisplayName(){  return null;  }

  public String getHelpTopic()  {  return "project.propStarteam";  }

  public Icon   getIcon()       {  return null;  }

  public JComponent createComponent() {
    myFldProject.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        selectProject();
      }
    });
    myFldView.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        selectView();
      }
    });
    myFldWorkingPath.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        selectWorkingPath();
      }
    });
    myBtnTest.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final View view = getView();

        if (view != null) {
          Messages.showMessageDialog(myPanel, StarteamBundle.message("message.text.connection.successful"),
                                     StarteamBundle.message("text.test.connection"), Messages.getInformationIcon());
        }
      }
    });
      return myPanel;
  }

  @Nullable
  private View getView()
  {
    final Project project = getProject();
    if (project == null) return null;

    final View[] views = project.getViews();
    String name = myFldView.getText();

    for (View view : views) {
      if (view.getName().equals(name)) return view;
    }

    Messages.showMessageDialog(myPanel, StarteamBundle.message("message.text.configuration.cannot.find.view"),
                               StarteamBundle.message("message.title.configuration.error"), Messages.getErrorIcon());
    return null;
  }

  private void selectView() {
    Project project = getProject();
    if (project == null) return;

    final View[] views = project.getViews();
    if (views.length == 0) {
      Messages.showMessageDialog(myPanel, StarteamBundle.message("message.text.configuration.no.views.found"),
                                 StarteamBundle.message("message.title.configuration.error"), Messages.getErrorIcon());
      return;
    }

    String[] names = new String[views.length];

    for (int i = 0; i < views.length; i++) {
      View view = views[i];
      names[i] = view.getName();
    }

    ArrayList<String> nameList = new ArrayList<String>(Arrays.asList(names));
    Collections.sort(nameList);
    String[] sortedNames = ArrayUtil.toStringArray(nameList);

    final int i = Messages.showChooseDialog(myPanel, StarteamBundle.message("request.text.configuration.select.view"),
                                            StarteamBundle.message("request.title.configuration.select.view"), sortedNames, sortedNames[0], Messages.getQuestionIcon());
    if (i < 0) return;
    myFldView.setText(sortedNames[i]);
  }

  @Nullable
  private Project getProject()
  {
    Server server = getServer();
    if (server == null) return null;

    final Project[] projects = server.getProjects();
    String name = myFldProject.getText();

    for (Project project : projects) {
      if (project.getName().equals(name)) return project;
    }

    Messages.showMessageDialog(myPanel, StarteamBundle.message("message.text.configuration.error.cannot.find.project"), StarteamBundle.message("message.title.configuration.error"), Messages.getErrorIcon());
    return null;
  }

  private void selectProject() {
    Server server = getServer();
    if (server == null) return;

    try {
      final Project[] projects = server.getProjects();

      if (projects.length == 0) {
        Messages.showMessageDialog(myPanel, StarteamBundle.message("message.text.configuration.error.no.projects.found"), StarteamBundle.message("message.title.configuration.error"), Messages.getErrorIcon());
        return;
      }

      String[] names = new String[projects.length];
      for (int i = 0; i < projects.length; i++) {
        Project project = projects[i];
        names[i] = project.getName();
      }

      ArrayList<String> nameList = new ArrayList<String>(Arrays.asList(names));
      Collections.sort(nameList);

      String[] sortedNames = ArrayUtil.toStringArray(nameList);

      final int i =
        Messages.showChooseDialog(myPanel,
                                  StarteamBundle.message("request.text.configuration.select.project"),
                                  StarteamBundle.message("request.text.title.select.project"),
                                  sortedNames, sortedNames[0], Messages.getQuestionIcon());

      if (i < 0) return;

      myFldProject.setText(sortedNames[i]);
    }
    finally {
      server.disconnect();
    }
  }

  private void selectWorkingPath()
  {
    Project project = getProject();
    if( project != null ) 
    {
      //  If the path is not defined, set the current project root's path. 
      if( myLastChosenDirectory == null )
      {
        VirtualFile baseDir = myProject.getBaseDir();
        //  baseDir may be null in the case of Project Template Settings.
        myLastChosenDirectory = (baseDir != null) ? VfsUtil.virtualToIoFile( baseDir ) : null;
      }
      
      JFileChooser chooser = new JFileChooser( myLastChosenDirectory );
      chooser.setFileSelectionMode( 1 );
      chooser.setDialogTitle( StarteamBundle.message("message.title.selectWorking.path") );
      if (chooser.showDialog(myPanel, CommonBundle.getOkButtonText()) == 0) {
        File selectedFile = chooser.getSelectedFile();
        if (selectedFile != null) {
          myFldWorkingPath.setText(selectedFile.getPath());
          myLastChosenDirectory = selectedFile;
        }
      }
    }
  }

  private Server getServer() {
    try {
      Server server = new Server(myFldServer.getText(), Integer.parseInt(myFldPort.getText()));

      server.logOn(myFldUser.getText(), new String(myFldPassword.getPassword()));
      return server;
    }
    catch (NumberFormatException e) {
      Messages.showMessageDialog(myPanel, StarteamBundle.message("message.text.configuration.invalid.port"), StarteamBundle.message("message.title.configuration.error"), Messages.getErrorIcon());
      LOG.debug(e);
      return null;
    }
    catch (Throwable e) {
      Messages.showMessageDialog(myPanel,
                                 StarteamBundle.message("message.text.configuration.cannot.connect.to.server", StarteamVcs.getMessage(e)),
                                 StarteamBundle.message("message.title.configuration.error"),
                                 Messages.getErrorIcon());
      LOG.debug(e);
      return null;
    }
  }

  public void reset()
  {
    StarteamConfiguration configuration = myProject.getComponent( StarteamConfiguration.class );

    myFldServer.setText( configuration.SERVER );
    myFldPort.setText( String.valueOf(configuration.PORT) );
    myFldUser.setText( configuration.USER );
    myFldPassword.setText( configuration.getPassword() );
    myFldProject.setText( configuration.PROJECT );
    myFldView.setText( configuration.VIEW );
    myFldWorkingPath.setText( configuration.ALTERNATIVE_WORKING_PATH );
    myCheckLockOnCheckout.setSelected( configuration.LOCK_ON_CHECKOUT );
    myCheckUnlockOnCheckin.setSelected( configuration.UNLOCK_ON_CHECKIN );
  }

  public void apply() throws ConfigurationException
  {
    boolean isChanged = isModified();
    StarteamConfiguration configuration = myProject.getComponent(StarteamConfiguration.class);

    configuration.SERVER = myFldServer.getText();
    configuration.PORT = Integer.parseInt(myFldPort.getText());
    configuration.USER = myFldUser.getText();
    configuration.setPassword( new String( myFldPassword.getPassword() ) );
    configuration.PROJECT = myFldProject.getText();
    configuration.VIEW = myFldView.getText();
    configuration.ALTERNATIVE_WORKING_PATH = myFldWorkingPath.getText();
    configuration.LOCK_ON_CHECKOUT = myCheckLockOnCheckout.isSelected();
    configuration.UNLOCK_ON_CHECKIN = myCheckUnlockOnCheckin.isSelected(); 

    if( isChanged )
    {
      //  If parameters are configured inproperly we will catch an exception
      //  inside the "getView" method and show it to the user. First of all we
      //  diagnose "Username/Password" configuration violations.
      //
      //  Otherwise we need to tell our host to reconnect to the server with
      //  new parameters.
      View view = getView();
      if( view == null )
        throw new ConfigurationException( StarteamBundle.message("message.title.configuration.error") );
      else
      {
        StarteamVcsAdapter host = myProject.getComponent( StarteamVcsAdapter.class );
        try
        {
          host.doShutdown();
          host.doStart();
        }
        catch( VcsException e )
        {
          Messages.showErrorDialog( StarteamBundle.message("message.text.configuration.cannot.connect.to.server", StarteamVcs.getMessage(e) ),
                                    StarteamBundle.message("message.title.configuration.error") );
        }
      }
    }
  }

  public boolean isModified()
  {
    StarteamConfiguration configuration = myProject.getComponent(StarteamConfiguration.class);

    final boolean equals = configuration.SERVER.equals( myFldServer.getText() ) &&
                           configuration.PORT == Integer.parseInt( myFldPort.getText() ) &&
                           configuration.USER.equals( myFldUser.getText() ) &&
                           configuration.getPassword().equals( new String( myFldPassword.getPassword() ) ) &&
                           configuration.PROJECT.equals( myFldProject.getText() ) &&
                           configuration.VIEW.equals( myFldView.getText() ) &&
                           configuration.ALTERNATIVE_WORKING_PATH.equals( myFldWorkingPath.getText() ) &&
                           (configuration.LOCK_ON_CHECKOUT == myCheckLockOnCheckout.isSelected() ) &&
                           (configuration.UNLOCK_ON_CHECKIN == myCheckUnlockOnCheckin.isSelected() ); 
    return !equals;
  }
}
