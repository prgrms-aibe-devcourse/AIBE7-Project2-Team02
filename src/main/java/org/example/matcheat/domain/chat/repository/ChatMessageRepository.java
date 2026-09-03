package org.example.matcheat.domain.chat.repository;

import org.example.matcheat.domain.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	// m.chatFile을 FETCH JOIN하여 단 1번의 쿼리로 파일 정보까지 일괄 조회
	@Query("SELECT m FROM ChatMessage m LEFT JOIN FETCH m.chatFile WHERE m.chatRoomId = :chatRoomId ORDER BY m.createdAt ASC")
	List<ChatMessage> findHistoryWithFilesByChatRoomId(@Param("chatRoomId") Long chatRoomId);

	@Query("""
			SELECT m
			FROM ChatMessage m
			LEFT JOIN FETCH m.chatFile
			WHERE m.chatRoomId IN :chatRoomIds
			  AND m.id = (
			      SELECT MAX(latest.id)
			      FROM ChatMessage latest
			      WHERE latest.chatRoomId = m.chatRoomId
			  )
			ORDER BY m.createdAt DESC
			""")
	List<ChatMessage> findLatestByChatRoomIds(@Param("chatRoomIds") List<Long> chatRoomIds);
}
