package br.com.hydroguard.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log=LoggerFactory.getLogger(ApiErrorHandler.class);
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String,Object>> notFound(Exception ex,HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND,"Recurso não encontrado",req,Map.of());
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String,Object>> method(Exception ex,HttpServletRequest req) {
        return error(HttpStatus.METHOD_NOT_ALLOWED,"Método HTTP não permitido",req,Map.of());
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String,Object>> media(Exception ex,HttpServletRequest req) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Tipo de conteúdo não suportado",req,Map.of());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> validacao(MethodArgumentNotValidException ex,HttpServletRequest req) {
        Map<String,String> fields=new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(),e.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST,"Dados inválidos: "+String.join(", ",fields.keySet()),req,fields);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,
        MissingServletRequestPartException.class,MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String,Object>> malformed(Exception ex,HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST,"JSON, parâmetro ou arquivo obrigatório inválido",req,Map.of());
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String,Object>> tamanho(Exception ex,HttpServletRequest req) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE,"A foto deve ter até 4 MB",req,Map.of());
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> http(ResponseStatusException ex,HttpServletRequest req) {
        return error(HttpStatus.valueOf(ex.getStatusCode().value()),ex.getReason()==null?"Erro na solicitação":ex.getReason(),req,Map.of());
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String,Object>> concurrency(Exception ex,HttpServletRequest req) {
        return error(HttpStatus.CONFLICT,"Ocorrência alterada por outra operação. Atualize e tente novamente",req,Map.of());
    }
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String,Object>> io(Exception ex,HttpServletRequest req) {
        return error(HttpStatus.SERVICE_UNAVAILABLE,"Armazenamento indisponível. Tente novamente",req,Map.of());
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> unexpected(Exception ex,HttpServletRequest req) {
        log.error("Falha interna: {}",ex.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR,"Não foi possível concluir a operação",req,Map.of());
    }
    private ResponseEntity<Map<String,Object>> error(HttpStatus status,String message,HttpServletRequest req,Map<String,String> fields) {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("timestamp",Instant.now().toString());body.put("status",status.value());body.put("message",message);
        body.put("path",req.getRequestURI());body.put("fieldErrors",fields);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
