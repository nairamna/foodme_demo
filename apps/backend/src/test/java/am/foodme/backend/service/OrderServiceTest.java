package am.foodme.backend.service;

import am.foodme.backend.dto.OrderDto;
import am.foodme.backend.exceptionHandler.BadRequestException;
import am.foodme.backend.exceptionHandler.NotFoundException;
import am.foodme.backend.model.Customer;
import am.foodme.backend.model.Order;
import am.foodme.backend.repository.ChefRepository;
import am.foodme.backend.repository.CustomerRepository;
import am.foodme.backend.repository.DishRepository;
import am.foodme.backend.repository.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** Customer-side order rules: KAN-5 self-service cancellation, and the cash-only check. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    private static final String OWNER = "ann@example.com";

    @Mock private OrderRepository orderRepository;
    @Mock private ChefRepository chefRepository;
    @Mock private DishRepository dishRepository;
    @Mock private CustomerRepository customerRepository;

    private SimpleMeterRegistry meterRegistry;
    private OrderService service;
    private Order stored;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new OrderService(orderRepository, chefRepository, dishRepository, customerRepository, meterRegistry);

        Customer owner = new Customer();
        owner.setId(1L);
        owner.setEmail(OWNER);
        stored = new Order();
        stored.setId(7L);
        stored.setNumber("FM-100007");
        stored.setStatus("NEW");
        stored.setCustomer(owner);

        // The repository behaves like a one-row table holding `stored`.
        when(orderRepository.findByNumber("FM-100007")).thenAnswer(inv -> Optional.of(stored));
        when(orderRepository.findByNumber("FM-404")).thenReturn(Optional.empty());
        when(orderRepository.findById(7L)).thenAnswer(inv -> Optional.of(stored));
        when(orderRepository.updateStatusIfCurrent(anyLong(), anyString(), anyString())).thenAnswer(inv -> {
            if (!inv.getArgument(1).equals(stored.getStatus())) {
                return 0;
            }
            stored.setStatus(inv.getArgument(2));
            return 1;
        });
    }

    @Test
    void cancelForCustomer_ownReceivedOrder_isCancelled() {
        OrderDto result = service.cancelForCustomer("FM-100007", OWNER);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        assertThat(stored.getStatus()).isEqualTo("CANCELLED");
        assertThat(meterRegistry.counter("foodme.orders.cancelled").count()).isEqualTo(1.0);
    }

    @Test
    void cancelForCustomer_emailWithDifferentCaseAndSpaces_stillMatchesOwner() {
        OrderDto result = service.cancelForCustomer("FM-100007", "  Ann@Example.COM ");

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCEPTED", "DELIVERED", "REJECTED", "CANCELLED"})
    void cancelForCustomer_orderPastReceived_isRefusedAndUnchanged(String status) {
        stored.setStatus(status);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.cancelForCustomer("FM-100007", OWNER));

        assertThat(ex.getMessage()).isEqualTo("Order FM-100007 can no longer be cancelled (status " + status + ")");
        assertThat(stored.getStatus()).isEqualTo(status);
        assertThat(meterRegistry.counter("foodme.orders.cancelled").count()).isZero();
    }

    @Test
    void cancelForCustomer_kitchenAcceptsMeanwhile_isRefusedWithCurrentStatus() {
        // The customer read NEW, but the kitchen accepts before the update runs.
        doAnswer(inv -> {
            stored.setStatus("ACCEPTED");
            return 0;
        }).when(orderRepository).updateStatusIfCurrent(7L, "NEW", "CANCELLED");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.cancelForCustomer("FM-100007", OWNER));

        assertThat(ex.getMessage()).endsWith("(status ACCEPTED)");
        assertThat(stored.getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void cancelForCustomer_someoneElsesOrder_looksNotFoundAndIsUnchanged() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.cancelForCustomer("FM-100007", "mallory@example.com"));

        assertThat(ex.getMessage()).isEqualTo("Order FM-100007 not found");
        assertThat(stored.getStatus()).isEqualTo("NEW");
    }

    @Test
    void cancelForCustomer_orderWithoutCustomer_looksNotFound() {
        stored.setCustomer(null);

        assertThrows(NotFoundException.class, () -> service.cancelForCustomer("FM-100007", OWNER));

        assertThat(stored.getStatus()).isEqualTo("NEW");
    }

    @Test
    void cancelForCustomer_unknownNumber_throwsNotFound() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.cancelForCustomer("FM-404", OWNER));

        assertThat(ex.getMessage()).isEqualTo("Order FM-404 not found");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CARD", "IDRAM", "cash"})
    void createOrder_nonCashPayment_isRefusedBeforeAnyLookup(String paymentType) {
        OrderDto request = new OrderDto();
        request.setPaymentType(paymentType);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.createOrder(request, OWNER));

        assertThat(ex.getMessage()).isEqualTo("Only CASH payment is supported");
        verifyNoInteractions(customerRepository, chefRepository, dishRepository);
    }
}
