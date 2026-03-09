package com.ray.atten.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Data;

@Data
public class Company extends BaseModel {

    private String name;

    @JsonIgnore
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    @JsonProperty("selected")
    public boolean isSelected() {
        return selected.get();
    }

    @JsonProperty("selected")
    public void setSelected(boolean val) {
        this.selected.set(val);
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }
}
