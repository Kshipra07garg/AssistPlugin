package com.example.demo;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class VirtualFileUserDialog {
    private final Project project;

    public VirtualFileUserDialog(Project project) {
        this.project = project;
    }

    public String chooseFileContent(){
        VirtualFile virtualFile = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFileDescriptor(), project, null
        );
        if(virtualFile!=null){
            try{
                byte[] content = virtualFile.contentsToByteArray();
                return new String(content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}