package br.com.hydroguard.dto;
/** Aggregate-only public dashboard; no protocols or personally identifying fields. */
public record DashboardResponse(long total, long abertas, long prioridadeAlta, long resolvidas) {}
