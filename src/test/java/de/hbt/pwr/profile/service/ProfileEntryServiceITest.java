package de.hbt.pwr.profile.service;

import de.hbt.pwr.profile.AbstractIntegrationTest;
import de.hbt.pwr.profile.data.NameEntityRepository;
import de.hbt.pwr.profile.data.ProfileRepository;
import de.hbt.pwr.profile.data.ProjectRepository;
import de.hbt.pwr.profile.data.SkillRepository;
import de.hbt.pwr.profile.model.Skill;
import de.hbt.pwr.profile.model.profile.LanguageSkillLevel;
import de.hbt.pwr.profile.model.profile.NameEntityType;
import de.hbt.pwr.profile.model.profile.Profile;
import de.hbt.pwr.profile.model.profile.entries.LanguageSkill;
import de.hbt.pwr.profile.model.profile.entries.NameEntity;
import de.hbt.pwr.profile.model.profile.entries.Project;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.transaction.Transactional;
import java.util.HashSet;

import static de.hbt.pwr.profile.model.profile.NameEntityType.COMPANY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class ProfileEntryServiceITest extends AbstractIntegrationTest {

    @Autowired
    private ProfileEntryService profileEntryService;
    @Autowired
    private ProfileRepository profileRepository;
    @Autowired
    private NameEntityRepository nameEntityRepository;
    @Autowired
    private SkillRepository skillRepository;
    @Autowired
    private ProjectRepository projectRepository;

    @Before
    public void setUp() throws Exception {
    }


    @Test
    public void saveLanguageToProfile() {
        Profile p = new Profile();
        p = profileRepository.save(p);
        LanguageSkill languageSkill = LanguageSkill.builder().level(LanguageSkillLevel.ADVANCED).language(NameEntity.builder().type(NameEntityType.LANGUAGE).name("Deutsch").build()).build();
        profileEntryService.updateProfileEntry(languageSkill, p, NameEntityType.LANGUAGE);

        assertThat(p.getLanguages())
                .extracting(LanguageSkill::getLevel, languageSkill1 -> languageSkill1.getNameEntity().getName())
                .containsExactly(tuple(LanguageSkillLevel.ADVANCED, "Deutsch"));

    }

    @Test
    public void deleteLanguageFromProfile() {
        Profile p = new Profile();
        p = profileRepository.save(p);
        LanguageSkill languageSkill = LanguageSkill.builder().id(null).level(LanguageSkillLevel.ADVANCED).language(NameEntity.builder().type(NameEntityType.LANGUAGE).name("Deutsch").build()).build();
        profileEntryService.updateProfileEntry(languageSkill, p, NameEntityType.LANGUAGE);

        assertThat(p.getLanguages())
                .extracting(LanguageSkill::getLevel, languageSkill1 -> languageSkill1.getNameEntity().getName())
                .containsExactly(tuple(LanguageSkillLevel.ADVANCED, "Deutsch"));

        profileEntryService.deleteEntryWithId(languageSkill.getId(), p, NameEntityType.LANGUAGE);

        assertThat(p.getLanguages().size()).isEqualTo(0);

    }

    @Test
    public void deleteLanguageFromProfileWithoutNameEntity() {
        Profile p = new Profile();
        p = profileRepository.save(p);
        NameEntity nameEntity = NameEntity.builder().id(967L).type(NameEntityType.LANGUAGE).name("Deutsch").build();
        LanguageSkill languageSkill = LanguageSkill.builder().level(LanguageSkillLevel.ADVANCED).language(nameEntity).build();
        profileEntryService.updateProfileEntry(languageSkill, p, NameEntityType.LANGUAGE);

        assertThat(p.getLanguages())
                .extracting(LanguageSkill::getLevel, languageSkill1 -> languageSkill1.getNameEntity().getName())
                .containsExactly(tuple(LanguageSkillLevel.ADVANCED, "Deutsch"));

        assertThat(nameEntityRepository.findByNameAndType(nameEntity.getName(), nameEntity.getType())).isNotNull();
        profileEntryService.deleteEntryWithId(languageSkill.getId(), p, NameEntityType.LANGUAGE);

        assertThat(p.getLanguages().size()).isEqualTo(0);

        assertThat(nameEntityRepository.findByNameAndType(nameEntity.getName(), nameEntity.getType())).isNotNull();
    }

    @Test
    public void addSkillToProfile() {
        Profile p = new Profile();
        p = profileRepository.save(p);
        Skill s = Skill.builder().name("hello").rating(5).build();
        s = profileEntryService.updateProfileSkills(s, p);

        assertThat(p.getSkills()).containsExactly(s);
    }

    @Test
    public void shouldAddSkillOnlyOnce() {
        Profile p = new Profile();
        p = profileRepository.save(p);
        Skill s = Skill.builder().name("hello").rating(5).build();
        s = profileEntryService.updateProfileSkills(s, p);
        s = profileEntryService.updateProfileSkills(s, p);

        assertThat(p.getSkills()).containsExactly(s);
    }

    @Test
    public void shouldUpdateSkillRating() {
        Profile p = new Profile();
        p = profileRepository.save(p);
        Skill s1 = Skill.builder().name("hello").rating(4).build();
        s1 = profileEntryService.updateProfileSkills(s1, p);
        p = profileRepository.save(p);
        Skill s2 = Skill.builder().name("hello").rating(5).build();
        s2 = profileEntryService.updateProfileSkills(s2, p);

        assertThat(p.getSkills()).containsExactly(s2);
    }

    @Test
    public void shouldAddProjectToProfile() {
        Profile p = new Profile();
        p = profileRepository.save(p);
        Project project = new Project();
        profileEntryService.updateProject(project, p);

        assertThat(p.getProjects().size()).isEqualTo(1);
    }

    @Test
    public void shouldDeleteProject() {
        // TODO: works when Project's equals and hashcode are not overridden
        Profile p = new Profile();
        Project project = new Project();
        project = projectRepository.saveAndFlush(project);
        p.getProjects().add(project);
        p = profileRepository.save(p);

        profileEntryService.deleteProject(p.getProjects().iterator().next().getId(), p);
        assertThat(p.getProjects().size()).isEqualTo(0);
    }


    @Test
    public void shouldAddSkillsFromProjectToProfile() {
        Profile p = new Profile();
        p = profileRepository.save(p);
        Skill s1 = Skill.builder().id(null).rating(3).name("S1").build();
        Skill s2 = Skill.builder().id(null).rating(4).name("S2").build();
        s2 = profileEntryService.updateProfileSkills(s2, p);
        Project project = Project.builder().id(null).skills(new HashSet<>()).build();
        project.getSkills().add(s1);
        project.getSkills().add(s2);
        profileEntryService.updateProject(project, p);

        assertThat(p.getProjects().size()).isEqualTo(1);
        assertThat(p.getProjects().iterator().next().getSkills()).contains(s1, s2);
        assertThat(p.getSkills()).contains(s1, s2);
    }


    @Test
    public void shouldPersistNewBrokerInProject() {
        Profile p = new Profile();
        NameEntity broker = NameEntity.builder().id(null).name("Test").type(COMPANY).build();
        Project project = Project.builder().id(null).broker(broker).build();
        p = profileRepository.save(p);
        profileEntryService.updateProject(project, p);
        Project savedProject = p.getProjects().iterator().next();
        assertThat(savedProject.getBroker().getId()).isNotNull();
        assertThat(savedProject.getBroker().getName()).isEqualTo(broker.getName());
        assertThat(savedProject.getBroker().getType()).isEqualTo(broker.getType());
    }

    @Test
    @Transactional
    public void shouldNotOverwriteExistingNameEntityNames() {
        NameEntity developmentCompany = nameEntityRepository.saveAndFlush(NameEntity.builder().name("Development Company").type(COMPANY).build());
        Profile otherProfile = profileRepository.save(new Profile());
        Project otherProject = projectRepository.save(Project.builder().id(null)
                .broker(developmentCompany)
                .build());
        otherProfile.getProjects().add(otherProject);
        otherProfile = profileRepository.saveAndFlush(otherProfile);

        Profile profile = profileRepository.save(new Profile());
        Project project = projectRepository.save(Project.builder().id(null)
                .broker(developmentCompany)
                .build());
        profile.getProjects().add(project);
        profile = profileRepository.saveAndFlush(profile);


        Project toSave = Project.builder()
                .id(project.getId())
                // We are changing dev company to hacking company, but we don't change the ID
                // This might happen through a client error, since we are using Entities as DTOs
                .broker(NameEntity.builder().id(developmentCompany.getId()).name("Hacking Company").type(COMPANY).build())
                .build();

        Project savedProject = profileEntryService.updateProject(toSave, profile);
        assertThat(savedProject.getBroker().getId()).isNotEqualTo(developmentCompany.getId());
        assertThat(savedProject.getBroker().getName()).isEqualTo("Hacking Company");

        Project savedOtherProject = projectRepository.getOne(otherProfile.getProjects().iterator().next().getId());
        assertThat(savedOtherProject.getBroker().getName()).isEqualTo(developmentCompany.getName());

    }

}
