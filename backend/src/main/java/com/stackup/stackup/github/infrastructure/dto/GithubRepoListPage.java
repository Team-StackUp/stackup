package com.stackup.stackup.github.infrastructure.dto;

import java.util.List;

public record GithubRepoListPage(List<GithubRepoResponse> repos, boolean hasNext) {
}
