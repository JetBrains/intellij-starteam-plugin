package com.intellij.vcs.starteam.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.actions.StandardVcsGroup;
import com.intellij.vcs.starteam.StarteamVcsAdapter;

public class StarteamGroup extends StandardVcsGroup
{
  public AbstractVcs getVcs(Project project)
  {
    return StarteamVcsAdapter.getInstance(project);
  }

  public String getVcsName(final Project project) {
    return "StarTeam";
  }
}
