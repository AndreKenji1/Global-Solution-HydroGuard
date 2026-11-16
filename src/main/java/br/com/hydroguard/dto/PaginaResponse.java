package br.com.hydroguard.dto;

import org.springframework.data.domain.Page;
import java.util.List;

/** Stable JSON contract independent of Spring Data's PageImpl serialization. */
public record PaginaResponse<T>(List<T> content,long totalElements,int totalPages,int number,int size,boolean first,boolean last) {
    public static <T> PaginaResponse<T> from(Page<T> page) {
        return new PaginaResponse<>(page.getContent(),page.getTotalElements(),page.getTotalPages(),
            page.getNumber(),page.getSize(),page.isFirst(),page.isLast());
    }
}
