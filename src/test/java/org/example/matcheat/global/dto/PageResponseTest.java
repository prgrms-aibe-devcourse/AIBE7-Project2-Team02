package org.example.matcheat.global.dto;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {
    @Test
    void filtersBeforeSlicingAndCapsPageSize() {
        PageResponse<Integer> page = PageResponse.from(List.of(1, 2, 3, 4, 5), 1, 2, value -> value % 2 == 1);

        assertThat(page.content()).containsExactly(5);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }
}
