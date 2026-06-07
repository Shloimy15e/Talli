package dev.dynamiq.talli.service.website;

import dev.dynamiq.talli.service.github.GithubFileChange;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface WebsiteContentAdapter {

    String type();

    List<String> expectedPaths();

    WebsiteEditorForm toEditorForm(Map<String, byte[]> files);

    List<GithubFileChange> apply(Long projectId,
                                  Map<String, byte[]> files,
                                  Map<String, String[]> params,
                                  MultiValueMap<String, MultipartFile> uploads);
}
