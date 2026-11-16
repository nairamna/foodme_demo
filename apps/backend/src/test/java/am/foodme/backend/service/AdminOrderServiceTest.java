package am.foodme.backend.service;

import am.foodme.backend.dto.OrderDto;
import am.foodme.backend.exceptionHandler.BadRequestException;
import am.foodme.backend.exceptionHandler.NotFoundException;
import am.foodme.backend.model.Order;
import am.foodme.backend.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** Status rules for the back office, including KAN-5: CANCELLED is final. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private AdminOrderService service;
    private Order stored;

    @BeforeEach
    void setUp() {
        service = new AdminOrderService(orderRepository);
        stored = new Order();
        stored.setId(7L);
        stored.setNumber("FM-100007");

        // The repository behaves like a one-row table holding `stored`.
        when(orderRepository.findById(7L)).thenAnswer(inv -> Optional.of(stored));
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        when(orderRepository.updateStatusIfCurrent(anyLong(), anyString(), anyString())).thenAnswer(inv -> {
            if (!inv.getArgument(1).equals(stored.getStatus())) {
                return 0;
            }
            stored.setStatus(inv.getArgument(2));
            return 1;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "NEW, ACCEPTED",
            "NEW, REJECTED",
            "ACCEPTED, DELIVERED",
            "ACCEPTED, REJECTED",
    })
    void updateStatus_allowedTransition_changesStatus(String from, String to) {
        stored.setStatus(from);

        OrderDto result = service.updateStatus(7L, to, null);

        assertThat(result.getStatus()).isEqualTo(to);
        assertThat(stored.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "NEW, DELIVERED",
            "NEW, CANCELLED",
            "ACCEPTED, NEW",
            "DELIVERED, ACCEPTED",
            "REJECTED, NEW",
    })
    void updateStatus_disallowedTransition_isRefusedAndUnchanged(String from, String to) {
        stored.setStatus(from);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.updateStatus(7L, to, null));

        assertThat(ex.getMessage()).isEqualTo("Cannot transition order from " + from + " to " + to);
        assertThat(stored.getStatus()).isEqualTo(from);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "ACCEPTED", "DELIVERED", "REJECTED", "CANCELLED"})
    void updateStatus_cancelledOrder_isFinal(String to) {
        stored.setStatus("CANCELLED");

        assertThrows(BadRequestException.class, () -> service.updateStatus(7L, to, null));

        assertThat(stored.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void updateStatus_rejected_storesReason() {
        stored.setStatus("NEW");

        OrderDto result = service.updateStatus(7L, "REJECTED", "Out of lavash");

        assertThat(result.getRejectReason()).isEqualTo("Out of lavash");
    }

    @Test
    void updateStatus_accepted_ignoresReason() {
        stored.setStatus("NEW");

        OrderDto result = service.updateStatus(7L, "ACCEPTED", "should not be stored");

        assertThat(result.getRejectReason()).isNull();
    }

    @Test
    void updateStatus_customerCancelsMeanwhile_isRefusedAndStaysCancelled() {
        stored.setStatus("NEW");
        // The admin read NEW, but the customer cancels before the update runs.
        doAnswer(inv -> {
            stored.setStatus("CANCELLED");
            return 0;
        }).when(orderRepository).updateStatusIfCurrent(7L, "NEW", "ACCEPTED");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.updateStatus(7L, "ACCEPTED", null));

        assertThat(ex.getMessage()).isEqualTo("Order 7 changed while updating; reload and try again");
        assertThat(stored.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void updateStatus_missingOrder_throwsNotFound() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.updateStatus(99L, "ACCEPTED", null));

        assertThat(ex.getMessage()).isEqualTo("Order 99 not found");
    }
}
