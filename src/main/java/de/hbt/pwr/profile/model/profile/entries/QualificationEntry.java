package de.hbt.pwr.profile.model.profile.entries;

import javax.persistence.Entity;
import java.time.LocalDate;

/**
 * Created by cg on 19.04.2017.
 */
@Entity
public class QualificationEntry extends ProfileEntry {

    private LocalDate date;

    public QualificationEntry() {
        // Default for JPA
    }

    public QualificationEntry(NameEntity nameEntity, LocalDate date) {
        super(nameEntity);
        this.date = date;
    }

    public QualificationEntry(Long id, LocalDate date, NameEntity qualification) {
        this.nameEntity = qualification;
        this.id = id;
        this.date = date;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    @Override
    public QualificationEntry copyNullId() {
        return new QualificationEntry(null, date, nameEntity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        QualificationEntry that = (QualificationEntry) o;

        return date != null ? date.equals(that.date) : that.date == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (date != null ? date.hashCode() : 0);
        return result;
    }
}
