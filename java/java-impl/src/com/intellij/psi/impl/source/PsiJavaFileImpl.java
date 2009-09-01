package com.intellij.psi.impl.source;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

public class PsiJavaFileImpl extends PsiJavaFileBaseImpl {
  public PsiJavaFileImpl(FileViewProvider file) {
    super(Constants.JAVA_FILE, Constants.JAVA_FILE, file);
  }

  public String toString(){
    return "PsiJavaFile:" + getName();
  }

  public Lexer createLexer() {
    return new JavaLexer(getLanguageLevel());
  }

  @NotNull
  @Override
  public GlobalSearchScope getResolveScope() {
    final VirtualFile file = getVirtualFile();
    if (file != null && !(file instanceof LightVirtualFile)) {
      final ProjectFileIndex index = ProjectRootManager.getInstance(getProject()).getFileIndex();
      if (!index.isInSource(file) && !index.isInLibraryClasses(file)) {
        return GlobalSearchScope.fileScope(this);
      }
    }
    return super.getResolveScope();
  }

  @NotNull
  public FileType getFileType() {
    return StdFileTypes.JAVA;
  }

  public void setPackageName(final String packageName) throws IncorrectOperationException {
    PsiPackageStatement packageStatement = getPackageStatement();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    if (packageStatement != null) {
      if (packageName.length() > 0) {
        packageStatement.replace(factory.createPackageStatement(packageName));
      }
      else {
        packageStatement.delete();
      }
    }
    else {
      if (packageName.length() > 0) {
        add(factory.createPackageStatement(packageName));
      }
    }
  }
}
