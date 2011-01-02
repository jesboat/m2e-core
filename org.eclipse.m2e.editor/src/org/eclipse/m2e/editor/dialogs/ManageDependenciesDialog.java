/*******************************************************************************
 * Copyright (c) 2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.m2e.editor.dialogs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.core.MavenLogger;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.ui.dialogs.AbstractMavenDialog;
import org.eclipse.m2e.editor.MavenEditorPlugin;
import org.eclipse.m2e.editor.composites.DependencyLabelProvider;
import org.eclipse.m2e.editor.composites.ListEditorContentProvider;
import org.eclipse.m2e.editor.internal.PomEdits;
import org.eclipse.m2e.model.edit.pom.Dependency;
import org.eclipse.m2e.model.edit.pom.DependencyManagement;
import org.eclipse.m2e.model.edit.pom.Model;
import org.eclipse.m2e.model.edit.pom.PomFactory;
import org.eclipse.m2e.model.edit.pom.PomPackage;
import org.eclipse.m2e.model.edit.pom.util.PomResourceFactoryImpl;
import org.eclipse.m2e.model.edit.pom.util.PomResourceImpl;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;


/**
 * This dialog is used to present the user with a list of dialogs that they can move to being managed under
 * "dependencyManagement". It allows them to pick the destination POM where the dependencies will be managed.
 * 
 * @author rgould
 */
public class ManageDependenciesDialog extends AbstractMavenDialog {

  protected static final String DIALOG_SETTINGS = ManageDependenciesDialog.class.getName();

  protected TableViewer dependenciesViewer;

  protected TreeViewer pomsViewer;

  protected Model model;

  LinkedList<MavenProject> projectHierarchy;

  private IStatus status;
  
  private List<Object> originalSelection;

  /**
   * Hierarchy is a LinkedList representing the hierarchy relationship between POM represented by model and its parents.
   * The head of the list should be the child, while the tail should be the root parent, with the others in between.
   */
  public ManageDependenciesDialog(Shell parent, Model model, LinkedList<MavenProject> hierarchy) {
    this(parent, model, hierarchy, null);
  }

  public ManageDependenciesDialog(Shell parent, Model model, LinkedList<MavenProject> hierarchy,
      List<Object> selection) {
    super(parent, DIALOG_SETTINGS);

    setShellStyle(getShellStyle() | SWT.RESIZE);
    setTitle(Messages.ManageDependenciesDialog_dialogTitle);

    this.model = model;
    this.projectHierarchy = hierarchy;
    this.originalSelection = selection;
  }

  /* (non-Javadoc)
   * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
   */
  protected Control createDialogArea(Composite parent) {
    readSettings();

    Composite composite = (Composite) super.createDialogArea(parent);

    Label infoLabel = new Label(composite, SWT.WRAP);
    infoLabel.setText(Messages.ManageDependenciesDialog_dialogInfo);

    Label horizontalBar = new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL);

    SashForm sashForm = new SashForm(composite, SWT.SMOOTH | SWT.HORIZONTAL);
    Composite dependenciesComposite = new Composite(sashForm, SWT.NONE);

    Label selectDependenciesLabel = new Label(dependenciesComposite, SWT.NONE);
    selectDependenciesLabel.setText(Messages.ManageDependenciesDialog_selectDependenciesLabel);

    final Table dependenciesTable = new Table(dependenciesComposite, SWT.FLAT | SWT.MULTI | SWT.BORDER);
    final TableColumn column = new TableColumn(dependenciesTable, SWT.NONE);
    dependenciesTable.addControlListener(new ControlAdapter() {
      @Override
      public void controlResized(ControlEvent e) {
        column.setWidth(dependenciesTable.getClientArea().width);
      }
    });

    Composite pomComposite = new Composite(sashForm, SWT.NONE);

    Label selectPomLabel = new Label(pomComposite, SWT.NONE);
    selectPomLabel.setText(Messages.ManageDependenciesDialog_selectPOMLabel);

    Tree pomTree = new Tree(pomComposite, SWT.BORDER);

    /*
     * Configure layouts
     */

    GridLayout layout = new GridLayout(1, false);
    composite.setLayout(layout);

    GridData gridData = new GridData(SWT.FILL, SWT.NONE, true, false);
    gridData.widthHint = 300;
    infoLabel.setLayoutData(gridData);

    gridData = new GridData(SWT.FILL, SWT.NONE, true, false);
    horizontalBar.setLayoutData(gridData);

    gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
    sashForm.setLayoutData(gridData);

    gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
    dependenciesComposite.setLayoutData(gridData);

    layout = new GridLayout(1, false);
    dependenciesComposite.setLayout(layout);

    gridData = new GridData(SWT.FILL, SWT.NONE, true, false);
    selectDependenciesLabel.setLayoutData(gridData);

    gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
    dependenciesTable.setLayoutData(gridData);

    gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
    pomComposite.setLayoutData(gridData);

    layout = new GridLayout(1, false);
    pomComposite.setLayout(layout);

    gridData = new GridData(SWT.FILL, SWT.NONE, true, false);
    selectPomLabel.setLayoutData(gridData);

    gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
    pomTree.setLayoutData(gridData);

    /*
     * Set up list/tree viewers
     */

    dependenciesViewer = new TableViewer(dependenciesTable);
    dependenciesViewer.setLabelProvider(new DependencyLabelProvider());
    dependenciesViewer.setContentProvider(new ListEditorContentProvider<Dependency>());
    //MNGECLIPSE-2675 only show the dependencies not already managed (decide just by absence of the version element
    List<Dependency> deps = model.getDependencies();
    List<Dependency> nonManaged = new ArrayList<Dependency>();
    if (deps != null) {
      for (Dependency d : deps) {
        if (d.getVersion() != null) {
          nonManaged.add(d);
        }
      }
    }
    dependenciesViewer.setInput(nonManaged);
    dependenciesViewer.addSelectionChangedListener(new DependenciesViewerSelectionListener());

    pomsViewer = new TreeViewer(pomTree);

    pomsViewer.setLabelProvider(new DepLabelProvider());

    pomsViewer.setContentProvider(new ContentProvider());
    pomsViewer.setInput(getProjectHierarchy());
    pomsViewer.addSelectionChangedListener(new PomViewerSelectionChangedListener());
    pomsViewer.expandAll();
    if(getProjectHierarchy().size() > 0) {
      pomsViewer.setSelection(new StructuredSelection(getProjectHierarchy().getLast()));
    }

    if(originalSelection != null && originalSelection.size() > 0) {
      dependenciesViewer.setSelection(new StructuredSelection(originalSelection));
    }
    
    return composite;
  }

  /* (non-Javadoc)
   * @see org.eclipse.ui.dialogs.SelectionStatusDialog#computeResult()
   */
  protected void computeResult() {
    MavenProject targetPOM = getTargetPOM();
    IMavenProjectFacade targetFacade = MavenPlugin.getDefault().getMavenProjectManager()
        .getMavenProject(targetPOM.getGroupId(), targetPOM.getArtifactId(), targetPOM.getVersion());
    MavenProject currentPOM = projectHierarchy.getFirst();
    IMavenProjectFacade currentFacade = MavenPlugin.getDefault().getMavenProjectManager()
        .getMavenProject(currentPOM.getGroupId(), currentPOM.getArtifactId(), currentPOM.getVersion());

    if (targetFacade == null || currentFacade == null) {
      return;
    }
    boolean same = targetPOM.equals(currentPOM);

    final LinkedList<Dependency> modelDeps = getDependenciesList();
    
    PomEdits.Operation removeVersionsOperation = new PomEdits.Operation() {
      public void process(Document document) {
        //we assume <dependencies> element exists here..
        List<Element> dependencies = PomEdits.findDependencies(document.getDocumentElement());
        assert dependencies != null;
        for (Element dep : dependencies) {
          String artifactId = PomEdits.getTextValue(PomEdits.findChild(dep, "artifactId"));
          String groupId = PomEdits.getTextValue(PomEdits.findChild(dep, "groupId"));
          //TODO: mkleint: nested cycles are ugly..
          for (Dependency md : modelDeps) {
            if (artifactId.equals(md.getArtifactId()) && groupId.equals(md.getGroupId())) {
              PomEdits.removeChild(dep, "version");
              break;
            }
          }
        }
      }
    };
    
    PomEdits.Operation manageOperation = new PomEdits.Operation() {
      public void process(Document document) {
        Element mands = PomEdits.getManagedDependencies(document.getDocumentElement());
        List<Dependency> modelDependencies = new ArrayList<Dependency>(modelDeps);
        List<Element> existing = PomEdits.findChilds(mands, "dependency");
        if (existing != null) {
          for (Element dep : existing) {
            String artifactId = PomEdits.getTextValue(PomEdits.findChild(dep, "artifactId"));
            String groupId = PomEdits.getTextValue(PomEdits.findChild(dep, "groupId"));
            //TODO: mkleint: nested cycles are ugly..
            //clone list, shall not modify shared resource (used by the remove operation)
            Iterator<Dependency> mdIter = modelDependencies.iterator();
            while (mdIter.hasNext()) {
              //TODO: here we iterate to find existing managed dependencies and decide not to overwrite them.
              // but this could eventually break the current project when the versions are diametrally different
              // we should have shown this information to the user in the UI in the first place (for him to decide what to do)
              Dependency md = mdIter.next();
              if (artifactId.equals(md.getArtifactId()) && groupId.equals(md.getGroupId())) {
                mdIter.remove();
                break;
              }
            }
          }
        }
        for (Dependency md : modelDependencies) {
          PomEdits.createDependency(mands, md.getGroupId(), md.getArtifactId(), md.getVersion());
        }
      }
    };
    
    try {
      if (same) {
        PomEdits.performOnDOMDocument(currentFacade.getPom(), new PomEdits.CompoundOperation(manageOperation, removeVersionsOperation));
      } else {
        PomEdits.performOnDOMDocument(targetFacade.getPom(), manageOperation);
        PomEdits.performOnDOMDocument(currentFacade.getPom(), removeVersionsOperation);
      }
    } catch(IOException e) {
      MavenLogger.log("", e);
    } catch(CoreException e) {
      MavenLogger.log(e);
    }
  }

  protected LinkedList<Dependency> getDependenciesList() {
    IStructuredSelection selection = (IStructuredSelection) dependenciesViewer.getSelection();

    LinkedList<Dependency> dependencies = new LinkedList<Dependency>();

    for(Object obj : selection.toArray()) {
      dependencies.add((Dependency) obj);
    }

    return dependencies;
  }

  protected LinkedList<MavenProject> getProjectHierarchy() {
    return this.projectHierarchy;
  }

  protected MavenProject getTargetPOM() {
    IStructuredSelection selection = (IStructuredSelection) pomsViewer.getSelection();
    return (MavenProject) selection.getFirstElement();
  }

  /**
   * Compare the list of selected dependencies against the selected targetPOM. If one of the dependencies is already
   * under dependencyManagement, but has a different version than the selected dependency, warn the user about this.
   * returns true if the user has been warned (but this method updates the status itself)
   * 
   * @param model
   * @param dependencies
   */
  protected boolean checkDependencies(org.apache.maven.model.Model model, LinkedList<Dependency> dependencies) {
    if(this.status != null && this.status.getCode() == IStatus.ERROR) {
      //Don't warn the user if there is already an error
      return false;
    }
    if(model == null || model.getDependencyManagement() == null
        || model.getDependencyManagement().getDependencies() == null
        || model.getDependencyManagement().getDependencies().isEmpty()) {
      return false;
    }

    for(Dependency selectedDep : dependencies) {
      for(org.apache.maven.model.Dependency targetDep : model.getDependencyManagement().getDependencies()) {
        if(selectedDep.getGroupId().equals(targetDep.getGroupId())
            && selectedDep.getArtifactId().equals(targetDep.getArtifactId())
            && !selectedDep.getVersion().equals(targetDep.getVersion())) {
          String modelID = model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion(); //$NON-NLS-1$ //$NON-NLS-2$
          if(targetDep.getLocation("") != null && targetDep.getLocation("").getSource() != null) { //$NON-NLS-1$ //$NON-NLS-2$
            modelID = targetDep.getLocation("").getSource().getModelId(); //$NON-NLS-1$
          }
          Object[] arguments = {selectedDep.getArtifactId() + "-" + selectedDep.getVersion(), //$NON-NLS-1$
              targetDep.getVersion(), modelID};
          String message = NLS.bind(Messages.ManageDependenciesDialog_dependencyExistsWarning, arguments);
          updateStatus(new Status(IStatus.WARNING, MavenEditorPlugin.PLUGIN_ID, message));
          return true;
        }
      }
    }
    return false;
  }

  protected void checkStatus(MavenProject targetProject, LinkedList<Dependency> selectedDependencies) {
    if(targetProject == null || selectedDependencies.isEmpty()) {
      updateStatus(new Status(IStatus.ERROR, MavenEditorPlugin.PLUGIN_ID, Messages.ManageDependenciesDialog_emptySelectionError));
      return;
    }
    boolean error = false;
    IMavenProjectFacade facade = MavenPlugin.getDefault().getMavenProjectManager()
        .getMavenProject(targetProject.getGroupId(), targetProject.getArtifactId(), targetProject.getVersion());
    if(facade == null) {
      error = true;
      updateStatus(new Status(IStatus.ERROR, MavenEditorPlugin.PLUGIN_ID,
          Messages.ManageDependenciesDialog_projectNotPresentError));
    } else {
      org.apache.maven.model.Model model = null;
      if(facade.getMavenProject() == null || facade.getMavenProject().getModel() == null) {
        try {
          model = MavenPlugin.getDefault().getMavenModelManager().readMavenModel(facade.getPom());
        } catch(CoreException e) {
          Object[] arguments = {facade.getPom(), e.getLocalizedMessage()};
          Status status = new Status(IStatus.ERROR, MavenEditorPlugin.PLUGIN_ID, NLS.bind(
              Messages.ManageDependenciesDialog_pomReadingError, arguments));
          MavenPlugin.getDefault().getLog().log(status);
          updateStatus(status);
          error = true;
        }
      } else {
        model = facade.getMavenProject().getModel();
      }
      if(model != null) {
        error = checkDependencies(model, getDependenciesList());
      }
    }

    if(!error) {
      clearStatus();
    }
  }

  protected void clearStatus() {
    updateStatus(new Status(IStatus.OK, MavenEditorPlugin.PLUGIN_ID, "")); //$NON-NLS-1$
  }

  protected class DependenciesViewerSelectionListener implements ISelectionChangedListener {
    public void selectionChanged(SelectionChangedEvent event) {
      checkStatus(getTargetPOM(), getDependenciesList());
    }
  }

  protected class PomViewerSelectionChangedListener implements ISelectionChangedListener {
    public void selectionChanged(SelectionChangedEvent event) {
      checkStatus(getTargetPOM(), getDependenciesList());
    }
  }

  @Override
  protected void updateStatus(IStatus status) {
    this.status = status;
    super.updateStatus(status);
  }

  public static class DepLabelProvider extends LabelProvider implements IColorProvider {
    /* (non-Javadoc)
     * @see org.eclipse.jface.viewers.LabelProvider#getText(java.lang.Object)
     */
    @Override
    public String getText(Object element) {
      MavenProject project = null;
      if(element instanceof MavenProject) {
        project = (MavenProject) element;
      } else if(element instanceof Object[]) {
        project = (MavenProject) ((Object[]) element)[0];
      } else {
        return ""; //$NON-NLS-1$
      }

      StringBuffer buffer = new StringBuffer();
      buffer.append(project.getGroupId() + " : " + project.getArtifactId() + " : " + project.getVersion()); //$NON-NLS-1$ //$NON-NLS-2$
      return buffer.toString();

    }

    public Color getForeground(Object element) {
      if(element instanceof MavenProject) {
        MavenProject project = (MavenProject) element;
        IMavenProjectFacade search = MavenPlugin.getDefault().getMavenProjectManager()
            .getMavenProject(project.getGroupId(), project.getArtifactId(), project.getVersion());
        if(search == null) {
          //This project is not in the workspace so we can't really modify it.
          return Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY);
        }
      }
      return null;
    }

    public Color getBackground(Object element) {
      return null;
    }
  }

  public class ContentProvider implements ITreeContentProvider {
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    }

    public void dispose() {
    }

    public boolean hasChildren(Object element) {
      Object[] children = getChildren(element);

      return children.length != 0;
    }

    public Object getParent(Object element) {
      if(element instanceof MavenProject) {
        MavenProject project = (MavenProject) element;
        return project.getParent();
      }
      return null;
    }

    /*
     * Return root element
     * (non-Javadoc)
     * @see org.eclipse.jface.viewers.ITreeContentProvider#getElements(java.lang.Object)
     */
    public Object[] getElements(Object inputElement) {

      if(inputElement instanceof LinkedList) {
        LinkedList<MavenProject> projects = (LinkedList<MavenProject>) inputElement;
        if(projects.isEmpty()) {
          return new Object[0];
        }
        return new Object[] {projects.getLast()};
      }

      return new Object[0];
    }

    public Object[] getChildren(Object parentElement) {
      if(parentElement instanceof MavenProject) {
        /*
         * Walk the hierarchy list until we find the parentElement and
         * return the previous element, which is the child.
         */
        MavenProject parent = (MavenProject) parentElement;

        if(getProjectHierarchy().size() == 1) {
          //No parent exists, only one element in the tree
          return new Object[0];
        }

        if(getProjectHierarchy().getFirst().equals(parent)) {
          //We are the final child
          return new Object[0];
        }

        ListIterator<MavenProject> iter = getProjectHierarchy().listIterator();
        while(iter.hasNext()) {
          MavenProject next = iter.next();
          if(next.equals(parent)) {
            iter.previous();
            MavenProject previous = iter.previous();
            return new Object[] {previous};
          }
        }
      }
      return new Object[0];
    }
  }
}
