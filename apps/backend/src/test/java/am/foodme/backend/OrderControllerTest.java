package am.foodme.backend;

import am.foodme.backend.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    private final AtomicInteger customerSeq = new AtomicInteger();

    private String customerToken() throws Exception {
        String email = "order-test-" + customerSeq.incrementAndGet() + "-" + UUID.randomUUID() + "@example.com";
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Ann",
                                "email", email,
                                "phoneNumber", "+37491234567",
                                "password", "secret123"
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String cashOrderPayload() throws Exception {
        Map<String, Object> body = Map.of(
                "chefId", 1,
                "receiverName", "Ann",
                "receiverPhoneNumber", "+37491234567",
                "receiverEmail", "ann@example.com",
                "paymentType", "CASH",
                "deliveryMethod", "TAKEAWAY",
                "note", "ring twice",
                "createOrderDishes", List.of(Map.of("dishId", 1, "quantity", 2))
        );
        return objectMapper.writeValueAsString(body);
    }

    @Order(3)
    @Test
    void createOrder_cashPayment_succeeds() throws Exception {
        String token = customerToken();
        String response = mockMvc.perform(post("/api/order")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashOrderPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.number").exists())
                .andReturn().getResponse().getContentAsString();
        String number = objectMapper.readTree(response).get("number").asText();

        mockMvc.perform(get("/api/customer/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.list[0].number").value(number));
    }

    @Test
    void createOrder_withoutToken_unauthorized() throws Exception {
        mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON).content(cashOrderPayload()))
                .andExpect(status().isUnauthorized());
    }

    @Order(4)
    @Test
    void createOrder_nonCashPayment_rejectedWithBadRequest() throws Exception {
        Map<String, Object> body = Map.of(
                "chefId", 1,
                "receiverName", "Ann",
                "receiverPhoneNumber", "+37491234567",
                "receiverEmail", "ann@example.com",
                "paymentType", "CARD",
                "deliveryMethod", "TAKEAWAY",
                "createOrderDishes", List.of(Map.of("dishId", 1, "quantity", 1))
        );
        mockMvc.perform(post("/api/order")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only CASH payment is supported"));
    }

    // FM-FLAKE-02
    @Order(1)
    @Test
    void createOrder_firstOrderGetsNumber100001() throws Exception {
        mockMvc.perform(post("/api/order")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashOrderPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("FM-100001"));
    }

    // FM-FLAKE-02
    @Order(2)
    @Test
    void getOrderByNumber_firstOrderIsFm100001() throws Exception {
        mockMvc.perform(post("/api/order")
                .header("Authorization", "Bearer " + customerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cashOrderPayload()));

        mockMvc.perform(get("/api/order/number/FM-100001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("FM-100001"));
    }

    // FM-FLAKE-04
    @Order(5)
    @Test
    void createOrder_createdAtFallsOnToday() throws Exception {
        String response = mockMvc.perform(post("/api/order")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashOrderPayload()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String number = objectMapper.readTree(response).get("number").asText();

        String orderResponse = mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String createdAt = objectMapper.readTree(orderResponse).get("createdAt").asText();

        assertTrue(createdAt.startsWith(LocalDate.now().toString()));
    }

    // KAN-5: customers cancel their own order before the kitchen accepts it.
    // Ordered after the FM-FLAKE-02 tests, which expect the first order numbers.

    private String placeOrder(String token) throws Exception {
        String response = mockMvc.perform(post("/api/order")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashOrderPayload()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("number").asText();
    }

    private void adminSetsStatus(String number, String newStatus, int expectedHttpStatus) throws Exception {
        Long id = orderRepository.findByNumber(number).orElseThrow().getId();
        mockMvc.perform(patch("/admin/order/" + id + "/status")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", newStatus))))
                .andExpect(status().is(expectedHttpStatus));
    }

    @Order(6)
    @Test
    void cancelOrder_receivedOrder_isCancelledAndShownInMyOrders() throws Exception {
        String token = customerToken();
        String number = placeOrder(token);

        mockMvc.perform(post("/api/customer/orders/" + number + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(number))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/customer/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list[0].status").value("CANCELLED"));
        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Order(7)
    @Test
    void cancelOrder_acceptedOrder_isRefused() throws Exception {
        String token = customerToken();
        String number = placeOrder(token);
        adminSetsStatus(number, "ACCEPTED", 200);

        mockMvc.perform(post("/api/customer/orders/" + number + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Order " + number + " can no longer be cancelled (status ACCEPTED)"));
        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Order(8)
    @Test
    void cancelOrder_someoneElsesOrder_isNotFoundAndUnchanged() throws Exception {
        String number = placeOrder(customerToken());

        mockMvc.perform(post("/api/customer/orders/" + number + "/cancel")
                        .header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Order(9)
    @Test
    void cancelOrder_withoutToken_unauthorized() throws Exception {
        String number = placeOrder(customerToken());
        mockMvc.perform(post("/api/customer/orders/" + number + "/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Order(10)
    @Test
    void cancelledOrder_isFinal_forCustomerAndAdmin() throws Exception {
        String token = customerToken();
        String number = placeOrder(token);
        mockMvc.perform(post("/api/customer/orders/" + number + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/customer/orders/" + number + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        for (String next : List.of("NEW", "ACCEPTED", "DELIVERED", "REJECTED")) {
            adminSetsStatus(number, next, 400);
        }
        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // KAN-9: customers rate their delivered orders; the chef's rating is the average.

    private String deliveredOrder(String token) throws Exception {
        String number = placeOrder(token);
        adminSetsStatus(number, "ACCEPTED", 200);
        adminSetsStatus(number, "DELIVERED", 200);
        return number;
    }

    private org.springframework.test.web.servlet.ResultActions review(String token, String number, Object rating, String comment)
            throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("rating", rating);
        if (comment != null) {
            body.put("comment", comment);
        }
        var request = post("/api/customer/orders/" + number + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    @Order(11)
    @Test
    void reviewOrder_deliveredOrder_isSavedAndShownEverywhere() throws Exception {
        String token = customerToken();
        String number = deliveredOrder(token);

        review(token, number, 4, "  Great lavash  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.rating").value(4))
                .andExpect(jsonPath("$.review.comment").value("Great lavash"))
                .andExpect(jsonPath("$.review.createdAt").exists());

        mockMvc.perform(get("/api/customer/orders").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.list[0].review.rating").value(4));
        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.review.comment").value("Great lavash"));
        Long id = orderRepository.findByNumber(number).orElseThrow().getId();
        mockMvc.perform(get("/admin/order/" + id).with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.review.rating").value(4));
    }

    @Order(12)
    @Test
    void reviewOrder_withoutComment_isSaved() throws Exception {
        String token = customerToken();
        String number = deliveredOrder(token);

        review(token, number, 5, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.rating").value(5))
                .andExpect(jsonPath("$.review.comment").doesNotExist());
    }

    @Order(13)
    @Test
    void reviewOrder_notDelivered_isRefused() throws Exception {
        String token = customerToken();
        String received = placeOrder(token);
        String accepted = placeOrder(token);
        adminSetsStatus(accepted, "ACCEPTED", 200);

        for (String number : List.of(received, accepted)) {
            review(token, number, 5, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Only delivered orders can be reviewed."));
        }
        mockMvc.perform(get("/api/order/number/" + received))
                .andExpect(jsonPath("$.review").doesNotExist());
    }

    @Order(14)
    @Test
    void reviewOrder_secondTime_isRefusedAndFirstRatingKept() throws Exception {
        String token = customerToken();
        String number = deliveredOrder(token);
        review(token, number, 2, "cold").andExpect(status().isOk());

        review(token, number, 5, "actually fine")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Order already reviewed."));
        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.review.rating").value(2))
                .andExpect(jsonPath("$.review.comment").value("cold"));
    }

    @Order(15)
    @Test
    void reviewOrder_someoneElsesOrder_looksNotFound() throws Exception {
        String number = deliveredOrder(customerToken());

        review(customerToken(), number, 5, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order " + number + " not found"));
        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.review").doesNotExist());
    }

    @Order(16)
    @Test
    void reviewOrder_withoutToken_unauthorized() throws Exception {
        String number = deliveredOrder(customerToken());
        review(null, number, 5, null).andExpect(status().isUnauthorized());
    }

    @Order(17)
    @Test
    void reviewOrder_invalidRatingOrComment_isRefused() throws Exception {
        String token = customerToken();
        String number = deliveredOrder(token);

        for (Object rating : java.util.Arrays.asList(0, 6, -1, 4.5, null)) {
            review(token, number, rating, null).andExpect(status().isBadRequest());
        }
        review(token, number, 3, "x".repeat(1001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Comment must be at most 1000 characters"));
        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.review").doesNotExist());

        review(token, number, 3, "x".repeat(1000)).andExpect(status().isOk());
    }

    @Order(18)
    @Test
    void reviewOrder_updatesChefRatingToRoundedAverage() throws Exception {
        // Chef 2 gets no other reviews in this suite, so its average is exactly these three.
        for (int rating : List.of(5, 4, 4)) {
            String token = customerToken();
            String response = mockMvc.perform(post("/api/order")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "chefId", 2,
                                    "receiverName", "Ann",
                                    "receiverPhoneNumber", "+37491234567",
                                    "receiverEmail", "ann@example.com",
                                    "paymentType", "CASH",
                                    "deliveryMethod", "TAKEAWAY",
                                    "createOrderDishes", List.of(Map.of("dishId", 3, "quantity", 1))
                            ))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String number = objectMapper.readTree(response).get("number").asText();
            adminSetsStatus(number, "ACCEPTED", 200);
            adminSetsStatus(number, "DELIVERED", 200);
            review(token, number, rating, null).andExpect(status().isOk());
        }

        // (5 + 4 + 4) / 3 = 4.333..., shown as 4.3
        mockMvc.perform(get("/api/chef/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4.3));
    }
}
