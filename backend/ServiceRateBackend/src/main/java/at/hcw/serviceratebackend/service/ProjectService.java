package at.hcw.serviceratebackend.service;
import at.hcw.serviceratebackend.model.entity.Organization;
import at.hcw.serviceratebackend.repository.OrganizationRepository;
import at.hcw.serviceratebackend.dto.CreateProjectRequest;
import at.hcw.serviceratebackend.dto.ProjectResponse;
import at.hcw.serviceratebackend.model.entity.Project;
import at.hcw.serviceratebackend.repository.ProjectRepository;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public ProjectResponse create(CreateProjectRequest request) {
        Organization organization = organizationRepository.findById(request.buyerOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        User user = userRepository.findById(request.createdByUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setBuyerOrganization(organization);
        project.setCreatedByUser(user);
        project.setTitle(request.title());
        project.setDescription(request.description());
        project.setProjectType(request.projectType());
        project.setWorkMode(request.workMode());
        project.setCurrencyCode("EUR");
        project.setVisibility("private_invited");
        project.setStatus("draft");

        Project saved = projectRepository.save(project);

        return new ProjectResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getProjectType(),
                saved.getWorkMode(),
                saved.getStatus()
        );
    }
}
