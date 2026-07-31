package com.jbank.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

  @Test
  void SpringData_Page를_공통_페이지_응답으로_변환한다() {
    // given
    PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 20), 2);

    // when
    PageResponse<String> response = PageResponse.from(page);

    // then
    assertThat(response.content()).containsExactly("a", "b");
    assertThat(response.page()).isEqualTo(0);
    assertThat(response.size()).isEqualTo(20);
    assertThat(response.totalElements()).isEqualTo(2);
    assertThat(response.totalPages()).isEqualTo(1);
  }
}
