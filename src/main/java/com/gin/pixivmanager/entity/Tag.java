package com.gin.pixivmanager.entity;

import lombok.Data;

import java.util.Objects;

@Data
public class Tag {
    Integer id, count = 0;
    String name, translation, trans;

    public Tag(String name, String translation) {
        this.name = name.toLowerCase();
        this.translation = translation;
    }

    public void setName(String name) {
        this.name = name.toLowerCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Tag tag = (Tag) o;
        return name.equals(tag.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public void addCount() {
        count++;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("Tag{");
        sb.append("count=").append(count);
        sb.append(", name='").append(name).append('\'');
        sb.append(", translation='").append(translation).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
