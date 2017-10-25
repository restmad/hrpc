package pw.hshen.hrpc.client;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.StringUtils;
import pw.hshen.hrpc.registry.ServiceDiscovery;
import pw.hshen.hrpc.common.annotation.RPCService;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;

/**
 * @author hongbin
 * Created on 21/10/2017
 */
@Slf4j
@RequiredArgsConstructor
public class RPCClientProxy implements BeanDefinitionRegistryPostProcessor {

    @NonNull
    private ServiceDiscovery serviceDiscovery;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        log.info("register beans");
        ClassPathScanningCandidateComponentProvider scanner = getScanner();
        scanner.addIncludeFilter(new AnnotationTypeFilter(RPCService.class));

        Set<BeanDefinition> candidateComponents = scanner
                .findCandidateComponents("pw.hshen");
        for (BeanDefinition candidateComponent : candidateComponents) {
            if (candidateComponent instanceof AnnotatedBeanDefinition) {
                // verify annotated class is an interface
                AnnotatedBeanDefinition beanDefinition = (AnnotatedBeanDefinition) candidateComponent;
                AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();

                BeanDefinitionHolder holder = createBeanDefinition(annotationMetadata);
                BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
            }
        }
    }

    private ClassPathScanningCandidateComponentProvider getScanner() {
        return new ClassPathScanningCandidateComponentProvider(false) {

            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                if (beanDefinition.getMetadata().isIndependent()) {

                    if (beanDefinition.getMetadata().isInterface()
                            && beanDefinition.getMetadata().getInterfaceNames().length == 1
                            && Annotation.class.getName().equals(beanDefinition.getMetadata().getInterfaceNames()[0])) {

                        try {

                            Class<?> target = Class.forName(beanDefinition.getMetadata().getClassName());

                            return !target.isAnnotation();
                        } catch (Exception ex) {

                            this.logger.error("Could not load target class: "
                                    + beanDefinition.getMetadata().getClassName(), ex);
                        }
                    }
                    return true;
                }
                return false;

            }
        };
    }

    private BeanDefinitionHolder createBeanDefinition(AnnotationMetadata annotationMetadata) {
        String className = annotationMetadata.getClassName();
        log.debug("Creating bean definition for class: {}", className);

        BeanDefinitionBuilder definition = BeanDefinitionBuilder.genericBeanDefinition(ProxyFactoryBean.class);
        String beanName = StringUtils.uncapitalize(className.substring(className.lastIndexOf('.') + 1));

        definition.addPropertyValue("type", className);
        definition.addPropertyValue("serviceDiscovery", serviceDiscovery);

        return new BeanDefinitionHolder(definition.getBeanDefinition(), beanName);
    }

    private String getMainClassName() {
        for (final Map.Entry<String, String> entry : System.getenv().entrySet()) {
            if (entry.getKey().startsWith("JAVA_MAIN_CLASS")) {
                String mainClass = entry.getValue();
                log.debug("Main class: {}", mainClass);
                return mainClass;
            }
        }
        throw new IllegalStateException("Cannot determine main class.");
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }
}
