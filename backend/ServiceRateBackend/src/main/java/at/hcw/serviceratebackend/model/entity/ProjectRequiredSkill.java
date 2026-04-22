package at.hcw.serviceratebackend.model.entity;

import at.hcw.serviceratebackend.model.entity.Skill;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "project_required_skills")
@IdClass(ProjectRequiredSkill.Pk.class)
public class ProjectRequiredSkill {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_position_id", nullable = false)
    private ProjectPosition projectPosition;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "is_mandatory", nullable = false)
    private Boolean mandatory;

    @Column(name = "min_level")
    private String minLevel;

    @Column(name = "min_years_experience")
    private Double minYearsExperience;

    @Getter
    @Setter
    public static class Pk implements Serializable {
        private UUID projectPosition;
        private UUID skill;
    }
}
