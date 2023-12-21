package com.github.jairoguo.gostructtojsonplugin;

import com.goide.psi.*;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.Objects;

public class CopyStructJsonAction extends AnAction {

    final String MESSAGE = "Unable to obtain GO structure, please place the cursor on the structure and right-click \"Copy Paste Special ->Copy Structure To JSON\"";

    @Override
    public void actionPerformed(AnActionEvent e) {

        Editor editor = e.getDataContext().getData(CommonDataKeys.EDITOR);
        assert editor != null;
        Project project = editor.getProject();
        Document document = editor.getDocument();

        String extension = Objects.requireNonNull(FileDocumentManager.getInstance().getFile(document)).getExtension();
        if (!(extension != null && extension.equalsIgnoreCase("go"))) {
            return;
        }
        GoFile psiFile = (GoFile) e.getDataContext().getData(CommonDataKeys.PSI_FILE);

        int offset = editor.getCaretModel().getOffset();

        assert psiFile != null;
        PsiElement element = psiFile.findElementAt(offset);
        GoTypeDeclaration goTypeDeclarationContext = PsiTreeUtil.getContextOfType(element, GoTypeDeclaration.class);

        if (goTypeDeclarationContext == null) {
            Notifier.notifyWarning(project, MESSAGE);
            return;
        }


        GoTypeSpec goTypeSpec = PsiTreeUtil.getChildOfType(goTypeDeclarationContext, GoTypeSpec.class);

        if (goTypeSpec == null) {
            Notifier.notifyWarning(project, MESSAGE);
        } else {
            GoSpecType goSpecType = goTypeSpec.getSpecType();

            GoStructType goStructType = PsiTreeUtil.getChildOfType(goSpecType, GoStructType.class);
            if (goStructType == null) {
                Notifier.notifyWarning(project, MESSAGE);
                return;
            }

            String goStructName = goTypeSpec.getIdentifier().getText();

            String result = Utils.convertGoStructToJson(goStructType);
            if (!Objects.equals(result, "error")) {
                StringSelection selection = new StringSelection(result);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selection, selection);
                String msg = "Successfully copied JSON of go structure \"" + goStructName + "\" to clipboard";
                Notifier.notifyInfo(project, msg + "\n" + result);
            } else {
                String err = "Failed copied JSON of go structure \"" + goStructName + "\" to clipboard; " + MESSAGE;
                Notifier.notifyError(project, err);
            }
        }

    }

}
