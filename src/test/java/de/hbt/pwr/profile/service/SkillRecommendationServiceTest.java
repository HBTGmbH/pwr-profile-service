package de.hbt.pwr.profile.service;

import de.hbt.pwr.profile.data.ProjectRepository;
import de.hbt.pwr.profile.model.Skill;
import de.hbt.pwr.profile.model.profile.entries.NameEntity;
import de.hbt.pwr.profile.model.profile.entries.Project;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.hbt.pwr.profile.model.profile.NameEntityType.COMPANY;
import static de.hbt.pwr.profile.model.profile.NameEntityType.PROJECT_ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class SkillRecommendationServiceTest {

    @Mock
    private ProjectRepository projectRepository;


    private List<Project> projects;

    @InjectMocks
    private SkillRecommendationService skillRecommendationService;

    private Project sampleProject;
    private Collection<String> defaultRoles = new ArrayList<>(Arrays.asList("Programmer", "QATester"));

    @Before
    public void setUp() {
        initMocks(this);
        sampleProject = new Project();
        sampleProject.setClient(NameEntity.builder().name("Starfleet").type(COMPANY).build());
        addDefaultRolesToProject(sampleProject);
        projects = new ArrayList<>();
        when(projectRepository.findAll()).thenReturn(projects);
    }

    private void addDefaultRolesToProject(Project project) {
        Set<NameEntity> roles = defaultRoles.stream()
                .map(r -> NameEntity.builder().name(r).type(PROJECT_ROLE).build())
                .collect(Collectors.toSet());
        project.setProjectRoles(roles);
    }

    private Long getMockId(String s) {
        return (long) (s.length() * 10 + projects.size());
    }

    private Project withExistingSkillsForCustomer(String customer, boolean mockId, String...skills) {
        Project project = Project.builder()
                .client(NameEntity.builder().name(customer).type(COMPANY).build())
                .skills(Stream.of(skills).map(
                        s -> Skill.builder()
                                .name(s)
                                .id(mockId ? getMockId(s) : null)
                                .build())
                        .collect(Collectors.toSet()))
                .build();
        addDefaultRolesToProject(project);
        projects.add(project);
        return project;
    }

    @Test
    public void shouldNotReturnNull() {
        assertThat(skillRecommendationService.getRecommendedSkills(null))
                .isNotNull();
    }

    @Test
    public void withoutProject_shouldReturnNoSkills() {
        Collection<Skill> recommendedSkills = skillRecommendationService.getRecommendedSkills(null);
        assertThat(recommendedSkills).isEmpty();
    }

    @Test
    public void withProjectForSpecificCustomer_shouldReturnCustomerSkills() {
        withExistingSkillsForCustomer("Starfleet", false, "Java", "JBoss");

        Collection<Skill> recommendedSkills = skillRecommendationService.getRecommendedSkills(sampleProject);

        assertThat(recommendedSkills)
                .extracting(Skill::getName)
                .containsExactlyInAnyOrder("Java", "JBoss");
    }

    @Test
    public void withProjectWithoutCustomer_shouldReturnNoSkills() {
        sampleProject.setClient(null);

        Collection<Skill> recommendedSkills = skillRecommendationService.getRecommendedSkills(sampleProject);

        assertThat(recommendedSkills).isEmpty();
    }

    @Test
    public void withExistingProject_ThatHasNoCustomer_shouldIgnore() {
        Project existingProject = withExistingSkillsForCustomer("Rebel Alliance", false, "shoot", "fight");
        existingProject.setClient(null);
        Collection<Skill> recommendedSkills = skillRecommendationService.getRecommendedSkills(sampleProject);

        assertThat(recommendedSkills).isEmpty();
    }

    @Test
    public void withExistingProject_ThatIs10YearsOld_shouldIgnore() {
        Project existingProject = withExistingSkillsForCustomer("Starfleet", false, "fly", "crash");
        existingProject.setStartDate(LocalDate.of(2005, 10, 21));
        existingProject.setEndDate(LocalDate.of(2009, 10, 21));
        Collection<Skill> recommendedSkills = skillRecommendationService.getRecommendedSkills(sampleProject);

        assertThat(recommendedSkills).isEmpty();
    }

    @Test
    public void shouldNotSuggestSkills_thatAreAlreadyPresent() {
        String blowingUp = "blow stuff up";
        String kesselRun = "kessel run";

        String newSkill = "lose limbs";
        withExistingSkillsForCustomer("Starfleet", false, blowingUp, kesselRun, newSkill);
        Set<Skill> sampleSkills = sampleProject.getSkills();
        sampleSkills.add(Skill.builder().name(newSkill).build());
        sampleProject.setSkills(sampleSkills);
        Collection<Skill> recommendedSkills = skillRecommendationService.getRecommendedSkills(sampleProject);

        assertThat(recommendedSkills)
                .extracting(Skill::getName)
                .containsExactlyInAnyOrder(blowingUp, kesselRun);
    }

    @Test
    public void withExistingProject_SameNameDifferentClient_shouldConsider() {
        Project existingProject = withExistingSkillsForCustomer("Rebel Alliance", false, "fly", "crash");
        existingProject.setName("Generic sample project");
        sampleProject.setName("Generic sample project");
        Collection<Skill> recommendedSkills = skillRecommendationService.getRecommendedSkills(sampleProject);

        assertThat(recommendedSkills)
                .extracting(Skill::getName)
                .containsExactlyInAnyOrder("fly", "crash");
    }

    @Test
    public void withExistingProject_SameClientDifferentRoles_shouldIgnore() {
        NameEntity roleTester = NameEntity.builder().name("Tester").type(PROJECT_ROLE).build();
        defaultRoles.clear();
        defaultRoles.add("Developer");
        Project existingProject = withExistingSkillsForCustomer("Starfleet", false, "Java", "JBoss");
        existingProject.setProjectRoles(new HashSet<>(Arrays.asList(roleTester)));
        Collection<Skill> recommendedSkills = skillRecommendationService.getRecommendedSkills(sampleProject);

        assertThat(recommendedSkills).isEmpty();
    }

    @Test
    public void withExistingProjects_SameSkillsDifferentProjects_shouldIgnoreDuplicates() {
        Project existingProject1 = withExistingSkillsForCustomer("Rebel Alliance", true, "fly", "crash");
        existingProject1.setName("Generic sample project");
        Project existingProject2 = withExistingSkillsForCustomer("Galactic Empire", true, "fly", "crash");
        existingProject2.setName("Generic sample project");
        sampleProject.setName("Generic sample project");
        Collection<Skill> recommendedSkills = skillRecommendationService.getRecommendedSkills(sampleProject);

        assertThat(recommendedSkills)
                .extracting(Skill::getName)
                .containsOnlyOnce("fly", "crash");
    }
}