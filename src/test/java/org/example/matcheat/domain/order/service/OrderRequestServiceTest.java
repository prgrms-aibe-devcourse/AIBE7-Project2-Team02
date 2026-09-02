package org.example.matcheat.domain.order.service;

import org.example.matcheat.common.location.GeocodingService;
import org.example.matcheat.domain.order.dto.OrderRequestCreateDTO;
import org.example.matcheat.domain.order.dto.OrderRequestResponseDTO;
import org.example.matcheat.domain.order.dto.OrderRequestUpdateDTO;
import org.example.matcheat.domain.order.entity.OrderRequest;
import org.example.matcheat.domain.order.enums.BudgetType;
import org.example.matcheat.domain.order.repository.OrderRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OrderRequestService의 주문 저장 및 주소 좌표 변환 로직을 검증한다.
 */
class OrderRequestServiceTest {

    private final OrderRequestRepository orderRequestRepository =
            mock(OrderRequestRepository.class);

    private final GeocodingService geocodingService =
            mock(GeocodingService.class);

    private final OrderRequestImageStorageService orderRequestImageStorageService =
            mock(OrderRequestImageStorageService.class);

    private final OrderRequestService orderRequestService =
            new OrderRequestService(
                    orderRequestRepository,
                    geocodingService,
                    orderRequestImageStorageService
            );

    @Test
    void 주문_생성_시_주소를_좌표로_변환해_저장한다() {
        Long buyerId = 4L;
        String address =
                "서울특별시 중구 세종대로 110";

        OrderRequestCreateDTO dto =
                mock(OrderRequestCreateDTO.class);

        when(dto.getTitle())
                .thenReturn("행사 도시락 주문");

        when(dto.getDescription())
                .thenReturn("점심 행사");

        when(dto.getEventDateTime())
                .thenReturn(
                        LocalDateTime.of(
                                2026,
                                9,
                                15,
                                12,
                                0
                        )
                );

        when(dto.getQuantity())
                .thenReturn(30);

        when(dto.getBudgetType())
                .thenReturn(BudgetType.PER_PERSON);

        when(dto.getBudget())
                .thenReturn(
                        BigDecimal.valueOf(20000)
                );

        when(dto.getCategory())
                .thenReturn("한식");

        when(dto.getDeliveryAddress())
                .thenReturn(address);

        when(geocodingService.geocode(address))
                .thenReturn(
                        new GeocodingService.Coordinates(
                                37.566370776634,
                                126.977918351844
                        )
                );

        when(orderRequestRepository.save(
                any(OrderRequest.class)
        )).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        OrderRequestResponseDTO result =
                orderRequestService.create(
                        buyerId,
                        dto
                );

        assertThat(result.getBuyerId())
                .isEqualTo(buyerId);

        assertThat(result.getDeliveryAddress())
                .isEqualTo(address);

        assertThat(result.getLatitude())
                .isEqualTo(37.566370776634);

        assertThat(result.getLongitude())
                .isEqualTo(126.977918351844);

        verify(geocodingService)
                .geocode(address);

        verify(orderRequestRepository)
                .save(any(OrderRequest.class));
    }

    @Test
    void 주문_주소를_수정하면_좌표도_다시_변환한다() {
        Long orderId = 1L;
        Long buyerId = 4L;

        OrderRequest orderRequest =
                OrderRequest.create(
                        buyerId,
                        "기존 주문",
                        "기존 설명",
                        LocalDateTime.of(
                                2026,
                                9,
                                15,
                                12,
                                0
                        ),
                        30,
                        BudgetType.PER_PERSON,
                        BigDecimal.valueOf(20000),
                        "한식",
                        "기존 주소",
                        "101동 202호",
                        37.5000,
                        127.0000
                );

        when(orderRequestRepository.findById(orderId))
                .thenReturn(
                        Optional.of(orderRequest)
                );

        OrderRequestUpdateDTO dto =
                mock(OrderRequestUpdateDTO.class);

        String newAddress =
                "서울특별시 중구 세종대로 110";

        when(dto.getDeliveryAddress())
                .thenReturn(newAddress);

        when(geocodingService.geocode(newAddress))
                .thenReturn(
                        new GeocodingService.Coordinates(
                                37.566370776634,
                                126.977918351844
                        )
                );

        OrderRequestResponseDTO result =
                orderRequestService.update(
                        orderId,
                        buyerId,
                        dto
                );

        assertThat(result.getDeliveryAddress())
                .isEqualTo(newAddress);

        assertThat(result.getLatitude())
                .isEqualTo(37.566370776634);

        assertThat(result.getLongitude())
                .isEqualTo(126.977918351844);

        verify(geocodingService)
                .geocode(newAddress);
    }
}