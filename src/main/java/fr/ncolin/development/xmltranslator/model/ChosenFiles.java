package fr.ncolin.development.xmltranslator.model;

import lombok.Getter;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChosenFiles {

    private PropertyChangeSupport support;

    @Getter
    String chosenFiles;

    @Getter
    private final List<File> files = new ArrayList<>();

    public ChosenFiles() {
        support = new PropertyChangeSupport(this);
    }

    public void setChosenFiles(String chosenFiles) {
        String oldProperty = this.chosenFiles;
        this.chosenFiles = chosenFiles;
        support.firePropertyChange("chosenFiles", oldProperty, chosenFiles);
    }

    public void addPropertyChangeListener(PropertyChangeListener pcl) {
        support.addPropertyChangeListener(pcl);
    }

    public void removePropertyChangeListener(PropertyChangeListener pcl) {
        support.removePropertyChangeListener(pcl);
    }
}
