package de.hbt.pwr.profile.service;

import de.hbt.pwr.profile.data.*;
import de.hbt.pwr.profile.errors.PwrValidationException;
import de.hbt.pwr.profile.model.Skill;
import de.hbt.pwr.profile.model.profile.NameEntityType;
import de.hbt.pwr.profile.model.profile.Profile;
import de.hbt.pwr.profile.model.profile.entries.*;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.logging.log4j.LogManager.getLogger;

/**
 * Service that allows various update operations on a {@link Profile}
 */
@Transactional
@Service
public class ProfileUpdateService {

    private static class Pair<K, V> {
        private K key;
        private V value;
        private Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }
        private K getKey() {
            return key;
        }
        private V getValue() {
            return value;
        }
    }

    private static final Logger LOG = getLogger(ProfileUpdateService.class);

    private final NameEntityRepository nameEntityRepository;
    private final SkillRepository skillRepository;
    private final ProfileEntryDAO profileEntryDAO;
    private final ProjectRepository projectRepository;
    private final ProfileRepository profileRepository;
    private final AdminNotificationService adminNotificationService;
    private final ProfileValidationService profileValidationService;

    @Autowired
    public ProfileUpdateService(NameEntityRepository nameEntityRepository, SkillRepository skillRepository,
                                ProfileEntryDAO profileEntryDAO, ProjectRepository projectRepository,
                                ProfileRepository profileRepository, AdminNotificationService adminNotificationService,
                                ProfileValidationService profileValidationService) {
        this.nameEntityRepository = nameEntityRepository;
        this.skillRepository = skillRepository;
        this.profileEntryDAO = profileEntryDAO;
        this.projectRepository = projectRepository;
        this.profileRepository = profileRepository;
        this.adminNotificationService = adminNotificationService;
        this.profileValidationService = profileValidationService;
    }


    /**
     * Invokes merging of given entity into the set of existing entities, while resolving potential conflicts.
     * <p>
     * This methods aims to provide consistency amoing {@link NameEntity} of the same {@link NameEntityType}.
     * Whenever a {@link NameEntity} is supposed to be new (i.E. it's {@link NameEntity#name} equals <code>null</code>,
     * it has to be checked that no concurrent {@link NameEntity} with the same {@link NameEntity#name} and
     * {@link NameEntity#type} exists.
     * </p>
     * <p>
     * This method checks if a concurrent entity exists. If one exists that describes the same {@link NameEntity}
     * (logically), the input <code>nameEntity</code> is discarded and the concurrent {@link NameEntity}
     * returned. If no concurrent entity exists, the input <code>nameEntity</code> is persistently stored
     * and returned.
     * </p>
     *
     * @param nameEntity    is the {@link NameEntity} that is supposed to be merged into the persistent storage
     * @param newEntityType is the type of the {@link NameEntity}, which is not necessarily set for new {@link NameEntity}s
     * @return either the concurrent or the given {@link NameEntity}
     */
    private Pair<Boolean, NameEntity> mergeNameEntity(@NotNull NameEntity nameEntity, NameEntityType newEntityType) {
        NameEntity res = nameEntity;
        Boolean created = false;
        LOG.debug("NameEntity: " + nameEntity.toString());
        List<NameEntity> names = nameEntityRepository.findAll();
        NameEntity concurrentEntity = nameEntityRepository.findByNameAndType(nameEntity.getName(), newEntityType);
        LOG.debug("Concurrent: " + (concurrentEntity == null ? null : concurrentEntity.toString()));
        if (concurrentEntity != null) {
            res = concurrentEntity;
        } else {
            res.setType(newEntityType);
            res = nameEntityRepository.saveAndFlush(nameEntity);
            created = true;
        }
        return new Pair<>(created, res);
    }

    private <T extends ProfileEntry> T persistEntry(T entry, Profile profile, NameEntityType nameEntityType) {
        Pair<Boolean, NameEntity> res = mergeNameEntity(entry.getNameEntity(), nameEntityType);
        entry.setNameEntity(res.getValue());
        entry = profileEntryDAO.update(entry);
        if (res.getKey()) {
            adminNotificationService.createProfileEntryNotification(profile, entry.getId(), res.getValue());
        }
        return entry;
    }


    /**
     * Persists the given set of entries and resolves conflicts while doing so.
     * Possible conflicts:
     * <ul>
     * <li>
     * The {@link ProfileEntry#getNameEntity()} has a null ID but a name entity with the same name already exists.
     * Reolsved by using the existing entity.
     * </li>
     * </ul>
     *
     * @param entries to be persisted
     * @return persisted entries
     */
    protected <T extends ProfileEntry> Set<T> persistEntries(Set<T> entries, Profile profile, NameEntityType nameEntityType) {
        return entries.stream().map(e -> persistEntry(e, profile, nameEntityType)).collect(Collectors.toSet());
    }


    private Skill importSkill(Profile profile, Skill skill, Map<String, Skill> skillsByName) {
        // Fix skill name
        boolean newSkillCreated = false;
        skill.setName(skill.getName().trim());
        Skill res;
        Skill concurrent = skillsByName.get(skill.getName().toLowerCase());
        LOG.debug("Skill: " + skill.toString());
        LOG.debug("Concurrent skill: " + (concurrent == null ? null : concurrent.toString()));
        if (concurrent == null) {
            // Only persist if the ID is null, otherwise, leave 'res = skill'
            if (skill.getId() == null) {
                res = skillRepository.saveAndFlush(skill);
                newSkillCreated = true;
            } else {
                res = skill;
            }
        } else {
            res = concurrent;
            if (skill.getRating() > concurrent.getRating()) {
                res.setRating(skill.getRating());
            }
            res = skillRepository.saveAndFlush(res);
        }
        // Do notifications
        adminNotificationService.createSkillNotification(profile, skill, newSkillCreated);
        return res;
    }

    /**
     * Imports Skills from a project and makes sure they are also in the set of profile skills.
     *
     * @param project defines the imported skills
     * @return the imported {@link Project} with possible changed skills in the project. The {@link Project#skills} will
     * have been replaced by a new Set.
     */
    protected Project importProjectSkills(Profile profile, Project project) {
        Set<Skill> profileSkills = profile.getSkills();
        Set<Skill> skills = project.getSkills();
        Map<String, Skill> profileSkillsByName = new HashMap<>();
        profileSkills.forEach(skill -> profileSkillsByName.put(skill.getName().toLowerCase(), skill));
        skills = skills.stream().map(skill -> {
            skill = importSkill(profile, skill, profileSkillsByName);
            profileSkillsByName.put(skill.getName().toLowerCase(), skill);
            // Only works on well defined hash code AND correctly persisted skill (With correct ID set)
            profileSkills.add(skill);
            return skill;
        }).collect(Collectors.toSet());
        project.setSkills(skills);
        return project;
    }

    private Project importProject(Project project) {
        Pair<Boolean, NameEntity> res;
        if (project.getBroker() != null) {
            res = mergeNameEntity(project.getBroker(), NameEntityType.COMPANY);
            project.setBroker(res.getValue());
        }
        if (project.getClient() != null) {
            res = mergeNameEntity(project.getClient(), NameEntityType.COMPANY);
            project.setClient(res.getValue());
        }
        project.setProjectRoles(project.getProjectRoles()
                .stream()
                .map(ne -> mergeNameEntity(ne, NameEntityType.PROJECT_ROLE).getValue())
                .collect(Collectors.toSet()));
        return projectRepository.saveAndFlush(project);
    }


    private void persistNameEntities(Profile profile) {
        if (profile.getEducation().size() > 0) {
            profile.setEducation(persistEntries(profile.getEducation(), profile, NameEntityType.EDUCATION));
        }

        if (profile.getQualification().size() > 0) {
            profile.setQualification(persistEntries(profile.getQualification(), profile, NameEntityType.QUALIFICATION));
        }

        if (profile.getLanguages().size() > 0) {
            profile.setLanguages(persistEntries(profile.getLanguages(), profile, NameEntityType.LANGUAGE));
        }

        if (profile.getSectors().size() > 0) {
            profile.setSectors(persistEntries(profile.getSectors(), profile, NameEntityType.SECTOR));
        }

        if (profile.getTrainingEntries().size() > 0) {
            profile.setTrainingEntries(persistEntries(profile.getTrainingEntries(), profile, NameEntityType.TRAINING));
        }

        if (profile.getCareerEntries().size() > 0) {
            profile.setCareerEntries(persistEntries(profile.getCareerEntries(), profile, NameEntityType.CAREER));
        }

        if (profile.getKeySkillEntries().size() > 0) {
            profile.setKeySkillEntries(persistEntries(profile.getKeySkillEntries(), profile, NameEntityType.KEY_SKILL));
        }
    }

    /**
     * Removes invalid {@link ProfileEntry} values. The following values are considered invalid:
     * <ul>
     * <li>{@link ProfileEntry#getNameEntity()} is <code>null</code></li>
     * <li>A {@link ProfileEntry} with the same {@link ProfileEntry#getNameEntity()} is already existing in the set
     * of entries</li>
     * </ul>
     *
     * @param entries the set of entries that is checked
     */
    private void removeInvalidEntries(Set<? extends ProfileEntry> entries) {
        Set<String> names = new HashSet<>();
        entries.removeIf(entry -> {
            boolean remove = false;
            if (entry.getNameEntity() == null) {
                remove = true;
            } else {
                if (names.contains(entry.getNameEntity().getName())) {
                    remove = true;
                }
                names.add(entry.getNameEntity().getName());
            }
            return remove;
        });
    }

    private void removeInvalidEducationEntries(Set<EducationEntry> entries) {
        Set<EducationEntry> educationEntries = new HashSet<>();
        entries.removeIf(entry -> {
            boolean remove = false;
            if (entry.getNameEntity() == null) {
                remove = true;
            } else {
                if (educationEntries.stream()
                        .anyMatch(ee -> ee.getNameEntity().getName().equals(entry.getNameEntity().getName())
                                && ee.getDegree().equals(entry.getDegree()))) {
                    remove = true;
                }
                educationEntries.add(entry);
            }
            return remove;
        });
    }

    /**
     * Checks for invalid entries. Invalid entries may be one of the following:
     * <ul>
     * <li>Entries that reference no {@link NameEntity}</li>
     * <li>{@link CareerElement} whose {@link CareerElement#startDate} is before {@link CareerElement#endDate}</li>
     * <li>Entries who share the same {@link NameEntity} (Duplicates)</li>
     * </ul>
     *
     * @param profile is the profile that is checked
     */
    private void removeInvalidEntries(Profile profile) {
        removeInvalidEducationEntries(profile.getEducation());
        removeInvalidEntries(profile.getLanguages());
        removeInvalidEntries(profile.getTrainingEntries());
        removeInvalidEntries(profile.getSectors());
        removeInvalidEntries(profile.getQualification());
        removeInvalidEntries(profile.getCareerEntries());
        removeInvalidEntries(profile.getKeySkillEntries());
    }

    private void importProjects(Profile profile) {
        Set<Project> projects = profile.getProjects()
                .stream()
                .map(this::importProject)
                .collect(Collectors.toSet());
        profile.setProjects(projects);
    }


    protected void importProfileSkills(Profile profile) {
        Map<String, Skill> skillsByName = new HashMap<>();
        profile.getSkills().forEach(skill -> {
            Skill res = importSkill(profile, skill, skillsByName);
            skillsByName.put(res.getName().toLowerCase(), res);
        });
        profile.setSkills(new HashSet<>(skillsByName.values()));
    }

    protected void importProjectSkills(Profile profile) {
        profile.setProjects(profile.getProjects().stream()
                .map(project -> importProjectSkills(profile, project))
                .collect(Collectors.toSet()));
    }


    public void importProfile(Profile profile) {
        Collection<String> errors = profileValidationService.validateProfile(profile);
        if (!errors.isEmpty()) {
            throw new PwrValidationException(errors);
        }
        LOG.info(profile.toString() + ": Importing profile.");
        removeInvalidEntries(profile);
        LOG.info(profile.toString() + ": Persisting name entities.");
        persistNameEntities(profile);
        // Note: order is important here. Cascading is deactivated, so
        // it is important to first persist all new profile skills,
        // then all project skills and THEN the projects.
        LOG.info(profile.toString() + ": Importing profile skills.");
        importProfileSkills(profile);
        LOG.info(profile.toString() + ": Importing project skills.");
        importProjectSkills(profile);
        LOG.info(profile.toString() + ": Importing projects.");
        importProjects(profile);
        LOG.info(profile.toString() + ": Importing done.");
    }

    public Profile updateProfile(Profile profile) {
        importProfile(profile);
        profile.setLastEdited(LocalDateTime.now());
        LOG.info("Profile getting saved...");
        profile = profileRepository.saveAndFlush(profile);
        LOG.info("Profile saved...");
        adminNotificationService.createProfileUpdatedNotification(profile);
        return profile;
    }


}
