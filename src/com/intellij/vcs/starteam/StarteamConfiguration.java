package com.intellij.vcs.starteam;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class StarteamConfiguration extends AbstractProjectComponent implements JDOMExternalizable {
  public String SERVER = "";
  public int PORT = 49201;
  public String USER = "";
  public String PASSWORD = "";
  public String PROJECT = "";
  public String VIEW = "";
  public String ALTERNATIVE_WORKING_PATH = "";
  public boolean LOCK_ON_CHECKOUT = false;
  public boolean UNLOCK_ON_CHECKIN = false;

  protected StarteamConfiguration(Project project) {
    super(project);
  }

  public String getPassword() {
    try {
      return PasswordUtil.decodePassword(PASSWORD);
    }
    catch (Exception e) {
      return "";
    }
  }

  public void setPassword(final String PWD) {
    PASSWORD = PasswordUtil.encodePassword(PWD);
  }

  @NotNull
  public String getComponentName() {
    return "StarteamConfiguration";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
