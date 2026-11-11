package com.sparkz.sparkz.availability;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AvailabilityTests {
    static final String A = "00000000-0000-0000-0000-000000000001";
    static final String B = "00000000-0000-0000-0000-000000000002";
    static final String ROOT = "/api/profiles/me/availability";
    @Autowired MockMvc mvc;
    @Autowired AvailabilityService service;
    @BeforeEach void days() { service.replaceDays(UUID.fromString(A), List.of(1, 2)); }

    @Test void slotLifecycleAndMultipleSlotsOnSameDay() throws Exception {
        mvc.perform(post(ROOT + "/slots").with(user(A)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"dayOfWeek\":1,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.slotId").isNumber());
        var slot = service.own(UUID.fromString(A)).slots().get(0);
        service.saveSlot(UUID.fromString(A), null, 1, java.time.LocalTime.NOON, java.time.LocalTime.of(13, 0));
        mvc.perform(get(ROOT).with(user(A))).andExpect(status().isOk()).andExpect(jsonPath("$.slots.length()").value(2));
        mvc.perform(put(ROOT + "/slots/" + slot.slotId()).with(user(A)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"dayOfWeek\":2,\"startTime\":\"14:00\",\"endTime\":\"15:00\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.dayOfWeek").value(2));
        mvc.perform(delete(ROOT + "/slots/" + slot.slotId()).with(user(A)).with(csrf())).andExpect(status().isNoContent());
        assertThat(service.own(UUID.fromString(A)).slots()).hasSize(1);
    }

    @Test void removedDaysRemoveOnlyTheirOwnSlots() throws Exception {
        var a = UUID.fromString(A);
        service.saveSlot(a, null, 1, java.time.LocalTime.NOON, java.time.LocalTime.of(13, 0));
        service.saveSlot(a, null, 2, java.time.LocalTime.NOON, java.time.LocalTime.of(13, 0));
        mvc.perform(put(ROOT + "/days").with(user(A)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"days\":[2,3]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.days[0]").value(2))
            .andExpect(jsonPath("$.slots.length()").value(1)).andExpect(jsonPath("$.slots[0].dayOfWeek").value(2));
        mvc.perform(put(ROOT + "/days").with(user(A)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"days\":[]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.days").isEmpty()).andExpect(jsonPath("$.slots").isEmpty());
    }

    @Test void otherUsersCannotReadUpdateOrDeleteSlots() throws Exception {
        var slot = service.saveSlot(UUID.fromString(A), null, 1, java.time.LocalTime.NOON, java.time.LocalTime.of(13, 0));
        mvc.perform(get(ROOT).with(user(B))).andExpect(status().isOk()).andExpect(jsonPath("$.slots").isEmpty());
        mvc.perform(put(ROOT + "/slots/" + slot.slotId()).with(user(B)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"dayOfWeek\":1,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(delete(ROOT + "/slots/" + slot.slotId()).with(user(B)).with(csrf())).andExpect(status().isNotFound());
        assertThat(service.own(UUID.fromString(A)).slots()).containsExactly(slot);
        mvc.perform(get("/api/profiles/" + A + "/availability").with(user(B))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"dayOfWeek\":1,\"startTime\":\"12:00\",\"endTime\":\"12:00\"}",
        "{\"dayOfWeek\":1,\"startTime\":\"13:00\",\"endTime\":\"12:00\"}",
        "{\"dayOfWeek\":1,\"startTime\":\"23:00\",\"endTime\":\"01:00\"}",
        "{\"dayOfWeek\":3,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}",
        "{\"dayOfWeek\":8,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}",
        "{\"dayOfWeek\":1,\"startTime\":\"25:00\",\"endTime\":\"10:00\"}",
        "{\"dayOfWeek\":1,\"endTime\":\"10:00\"}"
    })
    void rejectsInvalidSlots(String body) throws Exception {
        mvc.perform(post(ROOT + "/slots").with(user(A)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        assertThat(service.own(UUID.fromString(A)).slots()).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"{\"days\":[0]}", "{\"days\":[8]}", "{\"days\":[null]}", "{}"})
    void rejectsInvalidDaysWithoutLosingExistingData(String body) throws Exception {
        mvc.perform(put(ROOT + "/days").with(user(A)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        assertThat(service.own(UUID.fromString(A)).days()).containsExactly(1, 2);
    }

    @Test void authenticationAndCsrfAreRequired() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(put(ROOT + "/days").with(user(A)).contentType(MediaType.APPLICATION_JSON).content("{\"days\":[]}"))
            .andExpect(status().isForbidden());
    }
}
