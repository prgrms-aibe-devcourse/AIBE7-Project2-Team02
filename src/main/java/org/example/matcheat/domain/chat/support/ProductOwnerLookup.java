package org.example.matcheat.domain.chat.support;

/** Chat 도메인이 Product 엔티티를 직접 알지 않도록 분리한 조회 포트. */
public interface ProductOwnerLookup {
	/** 상품을 등록한 판매자의 회원(user) ID를 반환한다. 없으면 예외. */
	Long findOwnerAccountId(Long productId);
}