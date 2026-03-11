package com.ray.atten.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OaEmployee extends BaseModel implements Serializable {

    private String pin;
    private String name;
    private String company;
    private String dept;
    private String avatar;
    private String post;
    private Boolean inService;
    private String officeLocation;

    private LocalDateTime entryDate;

    private String fingerprint;

    private Integer fingerSize;

    private String photoBase64;

    private Integer photoSize;

    private String deviceSn;

    private Integer fid;

    @JsonIgnore
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    @JsonProperty("selected")
    public BooleanProperty selectedProperty() {
        return selected;
    }

    @JsonProperty("selected")
    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

}
