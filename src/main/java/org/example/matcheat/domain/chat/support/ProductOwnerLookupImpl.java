package org.example.matcheat.domain.chat.support;

import lombok.RequiredArgsConstructor;
import org.example.matcheat.domain.product.repository.ProductRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductOwnerLookupImpl implements ProductOwnerLookup {

	private final ProductRepository productRepository;

	@Override
	public Long findOwnerAccountId(Long productId) {
		// TODO: ProductEntity.ownerAccountId는 "로그인 구현 후 삭제 예정" 주석이 붙어있던 필드.
		// Product 팀이 실제 판매자 식별자로 교체하면 이 메서드도 그에 맞춰 같이 바꿔야 함.
		return productRepository.findById(productId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다. ID: " + productId))
				.getOwnerAccountId();
	}
}