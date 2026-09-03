package org.example.matcheat.domain.product.service;

import org.example.matcheat.common.location.GeocodingService;
import org.example.matcheat.domain.account.enums.SellerVerificationStatus;
import org.example.matcheat.domain.account.repository.SellerApplicationRepository;
import org.example.matcheat.domain.product.dto.ProductCreateDTO;
import org.example.matcheat.domain.product.dto.ProductResponseDTO;
import org.example.matcheat.domain.product.dto.ProductUpdateDTO;
import org.example.matcheat.domain.product.entity.ProductEntity;
import org.example.matcheat.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ProductService의 판매 조건 저장 및 주소 좌표 변환 로직을 검증한다.
 */
class ProductServiceTest {

    private final ProductRepository productRepository =
            mock(ProductRepository.class);

    private final ProductImageStorageService productImageStorageService =
            mock(ProductImageStorageService.class);

    private final GeocodingService geocodingService =
            mock(GeocodingService.class);

    private final SellerApplicationRepository sellerApplicationRepository =
            mock(SellerApplicationRepository.class);

    private final ProductService productService =
            new ProductService(
                    productRepository,
                    productImageStorageService,
                    geocodingService,
                    sellerApplicationRepository
            );

    @Test
    void 상품_생성_시_매장_주소를_좌표로_변환해_저장한다() {
        Long ownerAccountId = 5L;

        ProductCreateDTO dto =
                mock(ProductCreateDTO.class);

        String address =
                "서울특별시 중구 세종대로 110";

        when(
                sellerApplicationRepository.findStatusByUserId(
                        ownerAccountId
                )
        ).thenReturn(
                Optional.of(
                        SellerVerificationStatus.APPROVED
                )
        );

        when(dto.getProductName())
                .thenReturn("한식 도시락");

        when(dto.getMinHeadcount())
                .thenReturn(10);

        when(dto.getMaxHeadcount())
                .thenReturn(50);

        when(dto.getServingPrice())
                .thenReturn(15000);

        when(dto.getDeliveryRadiusKm())
                .thenReturn(10.0);

        when(dto.getStoreAddress())
                .thenReturn(address);

        when(dto.getCategory())
                .thenReturn("한식");

        when(dto.getDescription())
                .thenReturn("행사용 한식 도시락");

        when(dto.getDayOfWeek())
                .thenReturn(DayOfWeek.MONDAY);

        when(dto.getUnavailableDates())
                .thenReturn(List.of());

        when(geocodingService.geocode(address))
                .thenReturn(
                        new GeocodingService.Coordinates(
                                37.566370776634,
                                126.977918351844
                        )
                );

        when(
                productRepository.save(
                        any(ProductEntity.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        ProductResponseDTO result =
                productService.create(
                        dto,
                        null,
                        ownerAccountId
                );

        assertThat(result.getStoreAddress())
                .isEqualTo(address);

        assertThat(result.getLatitude())
                .isEqualTo(37.566370776634);

        assertThat(result.getLongitude())
                .isEqualTo(126.977918351844);

        verify(sellerApplicationRepository)
                .findStatusByUserId(ownerAccountId);

        verify(geocodingService)
                .geocode(address);

        verify(productRepository)
                .save(any(ProductEntity.class));
    }

    @Test
    void 상품_주소를_수정하면_좌표도_다시_변환한다() {
        Long productId = 1L;
        Long ownerAccountId = 5L;

        ProductEntity product =
                ProductEntity.create(
                        "기존 도시락",
                        10,
                        50,
                        15000,
                        10.0,
                        "기존 주소",
                        "101동 202호",
                        37.5000,
                        127.0000,
                        "한식",
                        "기존 상품",
                        DayOfWeek.MONDAY,
                        List.of(),
                        null,
                        ownerAccountId
                );

        when(
                productRepository.findByIdAndHiddenFalse(
                        productId
                )
        ).thenReturn(
                Optional.of(product)
        );

        ProductUpdateDTO dto =
                mock(ProductUpdateDTO.class);

        String newAddress =
                "서울특별시 중구 세종대로 110";

        when(dto.getStoreAddress())
                .thenReturn(newAddress);

        when(geocodingService.geocode(newAddress))
                .thenReturn(
                        new GeocodingService.Coordinates(
                                37.566370776634,
                                126.977918351844
                        )
                );

        ProductResponseDTO result =
                productService.update(
                        productId,
                        dto,
                        ownerAccountId
                );

        assertThat(result.getStoreAddress())
                .isEqualTo(newAddress);

        assertThat(result.getLatitude())
                .isEqualTo(37.566370776634);

        assertThat(result.getLongitude())
                .isEqualTo(126.977918351844);

        verify(geocodingService)
                .geocode(newAddress);
    }
}